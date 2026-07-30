#!/usr/bin/env bash
set -Eeuo pipefail

umask 0077

CONFIG_FILE="${XINYUAN_BACKUP_CONFIG:-/etc/xinyuan-gas/backup.env}"

if [[ ! -r "$CONFIG_FILE" ]]; then
  echo "Backup configuration is not readable: $CONFIG_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$CONFIG_FILE"

PROJECT_ROOT="${PROJECT_ROOT:-/var/www/xinyuan-gas}"
UPLOAD_ROOT="${UPLOAD_ROOT:-$PROJECT_ROOT/backend/storage/uploads}"
BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/xinyuan-gas}"
DB_NAME="${DB_NAME:-xinyuan_gas}"
DAILY_KEEP_COUNT="${DAILY_KEEP_COUNT:-14}"
WEEKLY_KEEP_COUNT="${WEEKLY_KEEP_COUNT:-8}"
MIN_FREE_MB="${MIN_FREE_MB:-2048}"
LOG_FILE="${LOG_FILE:-/var/log/xinyuan-gas-backup.log}"
NGINX_SITE_CONFIG="${NGINX_SITE_CONFIG:-/etc/nginx/sites-available/xinyuan-gas.conf}"

DAILY_ROOT="$BACKUP_ROOT/daily"
WEEKLY_ROOT="$BACKUP_ROOT/weekly"
STATE_ROOT="$BACKUP_ROOT/state"
LOCK_FILE="/run/lock/xinyuan-gas-backup.lock"
TEMP_PATH=""

log() {
  printf '%s [%s] %s\n' "$(date --iso-8601=seconds)" "$$" "$*"
}

fail() {
  log "ERROR: $*"
  exit 1
}

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

validate_config() {
  [[ "$BACKUP_ROOT" == /* ]] || fail "BACKUP_ROOT must be an absolute path"
  [[ "$BACKUP_ROOT" != "/" ]] || fail "BACKUP_ROOT cannot be /"
  [[ "$BACKUP_ROOT" == /var/backups/* ]] || fail "BACKUP_ROOT must stay under /var/backups"
  [[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fail "DB_NAME contains invalid characters"
  [[ -d "$UPLOAD_ROOT" ]] || fail "Upload directory does not exist: $UPLOAD_ROOT"
  [[ -d "$PROJECT_ROOT/backend" ]] || fail "Project backend does not exist: $PROJECT_ROOT/backend"
  is_positive_integer "$DAILY_KEEP_COUNT" || fail "DAILY_KEEP_COUNT must be positive"
  is_positive_integer "$WEEKLY_KEEP_COUNT" || fail "WEEKLY_KEEP_COUNT must be positive"
  is_positive_integer "$MIN_FREE_MB" || fail "MIN_FREE_MB must be positive"
}

prepare_runtime() {
  install -d -m 0700 "$BACKUP_ROOT" "$DAILY_ROOT" "$WEEKLY_ROOT" "$STATE_ROOT"
  install -d -m 0750 "$(dirname "$LOG_FILE")"
  touch "$LOG_FILE"
  chmod 0600 "$LOG_FILE"
  exec > >(tee -a "$LOG_FILE") 2>&1

  exec 9>"$LOCK_FILE"
  flock -n 9 || fail "Another backup process is already running"
}

cleanup() {
  local exit_code=$?
  if [[ -n "$TEMP_PATH" && -e "$TEMP_PATH" ]]; then
    rm -rf -- "$TEMP_PATH"
  fi
  if (( exit_code != 0 )); then
    log "Backup command failed with exit code $exit_code"
  fi
}

check_free_space() {
  local available_kb required_kb
  available_kb="$(df -Pk "$BACKUP_ROOT" | awk 'NR == 2 {print $4}')"
  required_kb=$((MIN_FREE_MB * 1024))
  [[ "$available_kb" =~ ^[0-9]+$ ]] || fail "Unable to determine free disk space"
  (( available_kb >= required_kb )) || fail "Available disk space is below ${MIN_FREE_MB} MB"
}

dump_database() {
  local output_file="$1"
  local partial_file="${output_file}.partial"

  mysqldump \
    --single-transaction \
    --quick \
    --routines \
    --triggers \
    --events \
    --hex-blob \
    --default-character-set=utf8mb4 \
    --no-tablespaces \
    "$DB_NAME" | gzip -9 > "$partial_file"

  gzip -t "$partial_file"
  mv "$partial_file" "$output_file"
}

write_manifest() {
  local destination="$1"
  local backup_type="$2"
  local upload_files upload_bytes db_sha

  upload_files="$(find "$destination/uploads" -type f | wc -l | tr -d ' ')"
  upload_bytes="$(du -sb "$destination/uploads" | awk '{print $1}')"
  db_sha="$(sha256sum "$destination/database.sql.gz" | awk '{print $1}')"

  {
    printf 'format_version=1\n'
    printf 'backup_type=%s\n' "$backup_type"
    printf 'created_at=%s\n' "$(date --iso-8601=seconds)"
    printf 'hostname=%s\n' "$(hostname)"
    printf 'database=%s\n' "$DB_NAME"
    printf 'database_sha256=%s\n' "$db_sha"
    printf 'upload_files=%s\n' "$upload_files"
    printf 'upload_bytes=%s\n' "$upload_bytes"
  } > "$destination/manifest.env"

  (
    cd "$destination"
    sha256sum database.sql.gz manifest.env > SHA256SUMS
  )
}

prune_snapshot_directories() {
  local root="$1"
  local keep="$2"
  local index
  local -a snapshots=()

  mapfile -t snapshots < <(
    find "$root" -mindepth 1 -maxdepth 1 -type d ! -name '.*' -printf '%f\n' |
      sort -r
  )

  for ((index = keep; index < ${#snapshots[@]}; index++)); do
    [[ "${snapshots[$index]}" =~ ^[0-9]{8}_[0-9]{6}$ ]] ||
      fail "Refusing to prune unexpected snapshot: ${snapshots[$index]}"
    rm -rf -- "$root/${snapshots[$index]}"
    log "Pruned old daily snapshot: ${snapshots[$index]}"
  done
}

prune_weekly_archives() {
  local index archive_name
  local -a archives=()

  mapfile -t archives < <(
    find "$WEEKLY_ROOT" -mindepth 1 -maxdepth 1 -type f \
      -name 'xinyuan-gas-full-*.tar.gz' -printf '%f\n' |
      sort -r
  )

  for ((index = WEEKLY_KEEP_COUNT; index < ${#archives[@]}; index++)); do
    archive_name="${archives[$index]}"
    [[ "$archive_name" =~ ^xinyuan-gas-full-[0-9]{8}_[0-9]{6}\.tar\.gz$ ]] ||
      fail "Refusing to prune unexpected archive: $archive_name"
    rm -f -- "$WEEKLY_ROOT/$archive_name" "$WEEKLY_ROOT/$archive_name.sha256"
    log "Pruned old weekly archive: $archive_name"
  done
}

run_daily() {
  local stamp final_path previous_snapshot

  stamp="$(date +%Y%m%d_%H%M%S)"
  final_path="$DAILY_ROOT/$stamp"
  TEMP_PATH="$DAILY_ROOT/.${stamp}.tmp.$$"

  mkdir -p "$TEMP_PATH/uploads"
  log "Starting daily database backup"
  dump_database "$TEMP_PATH/database.sql.gz"

  previous_snapshot="$(
    find "$DAILY_ROOT" -mindepth 1 -maxdepth 1 -type d ! -name '.*' -printf '%p\n' |
      sort |
      tail -n 1
  )"

  log "Starting incremental upload snapshot"
  if [[ -n "$previous_snapshot" && -d "$previous_snapshot/uploads" ]]; then
    rsync -a --delete --numeric-ids \
      --link-dest="$previous_snapshot/uploads" \
      "$UPLOAD_ROOT/" "$TEMP_PATH/uploads/"
  else
    rsync -a --delete --numeric-ids "$UPLOAD_ROOT/" "$TEMP_PATH/uploads/"
  fi

  write_manifest "$TEMP_PATH" "daily"
  mv "$TEMP_PATH" "$final_path"
  TEMP_PATH=""

  printf '%s\n' "$final_path" > "$STATE_ROOT/latest-daily"
  printf '%s\n' "$(date --iso-8601=seconds)" > "$STATE_ROOT/daily-last-success"
  prune_snapshot_directories "$DAILY_ROOT" "$DAILY_KEEP_COUNT"
  log "Daily backup completed: $final_path"
}

copy_recovery_config() {
  local destination="$1"

  mkdir -p "$destination/server-config"
  if [[ -r "$PROJECT_ROOT/backend/.env" ]]; then
    install -m 0600 "$PROJECT_ROOT/backend/.env" "$destination/server-config/backend.env"
  fi
  if [[ -r "$CONFIG_FILE" ]]; then
    install -m 0600 "$CONFIG_FILE" "$destination/server-config/backup.env"
  fi
  if [[ -r "$NGINX_SITE_CONFIG" ]]; then
    install -m 0600 "$NGINX_SITE_CONFIG" "$destination/server-config/xinyuan-gas.nginx.conf"
  fi
}

run_weekly() {
  local stamp archive_name archive_path archive_partial

  stamp="$(date +%Y%m%d_%H%M%S)"
  archive_name="xinyuan-gas-full-${stamp}.tar.gz"
  archive_path="$WEEKLY_ROOT/$archive_name"
  archive_partial="$WEEKLY_ROOT/.${archive_name}.partial.$$"
  TEMP_PATH="$WEEKLY_ROOT/.${stamp}.stage.$$"

  mkdir -p "$TEMP_PATH/uploads"
  log "Starting weekly full database backup"
  dump_database "$TEMP_PATH/database.sql.gz"

  log "Copying full upload set"
  rsync -a --delete --numeric-ids "$UPLOAD_ROOT/" "$TEMP_PATH/uploads/"
  copy_recovery_config "$TEMP_PATH"
  write_manifest "$TEMP_PATH" "weekly-full"

  log "Creating weekly full archive"
  tar -C "$TEMP_PATH" -czf "$archive_partial" \
    database.sql.gz uploads server-config manifest.env SHA256SUMS
  tar -tzf "$archive_partial" >/dev/null
  mv "$archive_partial" "$archive_path"
  (
    cd "$WEEKLY_ROOT"
    sha256sum "$archive_name" > "$archive_name.sha256"
  )

  rm -rf -- "$TEMP_PATH"
  TEMP_PATH=""
  printf '%s\n' "$archive_path" > "$STATE_ROOT/latest-weekly"
  printf '%s\n' "$(date --iso-8601=seconds)" > "$STATE_ROOT/weekly-last-success"
  prune_weekly_archives
  log "Weekly full backup completed: $archive_path"
}

verify_daily() {
  local snapshot="${1:-}"
  local expected_files actual_files

  if [[ -z "$snapshot" && -r "$STATE_ROOT/latest-daily" ]]; then
    snapshot="$(<"$STATE_ROOT/latest-daily")"
  fi
  [[ -n "$snapshot" && -d "$snapshot" ]] || fail "Daily snapshot was not found"
  [[ "$snapshot" == "$DAILY_ROOT/"* ]] || fail "Snapshot is outside the daily backup root"

  gzip -t "$snapshot/database.sql.gz"
  (
    cd "$snapshot"
    sha256sum --check SHA256SUMS
  )

  # shellcheck disable=SC1090
  source "$snapshot/manifest.env"
  expected_files="${upload_files:?Missing upload_files in manifest}"
  actual_files="$(find "$snapshot/uploads" -type f | wc -l | tr -d ' ')"
  [[ "$actual_files" == "$expected_files" ]] ||
    fail "Upload file count mismatch: expected $expected_files, got $actual_files"
  log "Daily snapshot verified: $snapshot ($actual_files upload files)"
}

verify_weekly() {
  local archive="${1:-}"

  if [[ -z "$archive" && -r "$STATE_ROOT/latest-weekly" ]]; then
    archive="$(<"$STATE_ROOT/latest-weekly")"
  fi
  [[ -n "$archive" && -f "$archive" ]] || fail "Weekly archive was not found"
  [[ "$archive" == "$WEEKLY_ROOT/"* ]] || fail "Archive is outside the weekly backup root"

  (
    cd "$WEEKLY_ROOT"
    sha256sum --check "$(basename "$archive").sha256"
  )
  tar -tzf "$archive" >/dev/null
  log "Weekly archive verified: $archive"
}

restore_check() {
  local snapshot="${1:-}"
  local check_db table_count record_count

  if [[ -z "$snapshot" && -r "$STATE_ROOT/latest-daily" ]]; then
    snapshot="$(<"$STATE_ROOT/latest-daily")"
  fi
  [[ -n "$snapshot" && -f "$snapshot/database.sql.gz" ]] ||
    fail "Daily database backup was not found"

  check_db="xinyuan_restore_check_$(date +%Y%m%d%H%M%S)_$$"
  trap 'mysql -e "DROP DATABASE IF EXISTS \`'"$check_db"'\`" >/dev/null 2>&1 || true; cleanup' EXIT

  log "Creating temporary restore-check database: $check_db"
  mysql -e "CREATE DATABASE \`$check_db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
  gzip -dc "$snapshot/database.sql.gz" | mysql "$check_db"
  table_count="$(mysql -Nse \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$check_db'")"
  record_count="$(mysql -Nse \
    "SELECT COUNT(*) FROM \`$check_db\`.inspection_records")"
  [[ "$table_count" =~ ^[1-9][0-9]*$ ]] || fail "Restored database contains no tables"
  [[ "$record_count" =~ ^[0-9]+$ ]] || fail "Unable to count restored records"
  mysql -e "DROP DATABASE \`$check_db\`"
  trap cleanup EXIT
  log "Restore check passed: $table_count tables, $record_count inspection records"
}

show_status() {
  local daily_success="never"
  local weekly_success="never"
  local daily_path="none"
  local weekly_path="none"

  [[ -r "$STATE_ROOT/daily-last-success" ]] &&
    daily_success="$(<"$STATE_ROOT/daily-last-success")"
  [[ -r "$STATE_ROOT/weekly-last-success" ]] &&
    weekly_success="$(<"$STATE_ROOT/weekly-last-success")"
  [[ -r "$STATE_ROOT/latest-daily" ]] &&
    daily_path="$(<"$STATE_ROOT/latest-daily")"
  [[ -r "$STATE_ROOT/latest-weekly" ]] &&
    weekly_path="$(<"$STATE_ROOT/latest-weekly")"

  printf 'daily_last_success=%s\n' "$daily_success"
  printf 'daily_latest=%s\n' "$daily_path"
  printf 'weekly_last_success=%s\n' "$weekly_success"
  printf 'weekly_latest=%s\n' "$weekly_path"
  printf 'backup_size=%s\n' "$(du -sh "$BACKUP_ROOT" | awk '{print $1}')"
  printf 'disk_available=%s\n' "$(df -h "$BACKUP_ROOT" | awk 'NR == 2 {print $4}')"
}

usage() {
  cat <<'EOF'
Usage:
  xinyuan-gas-backup daily
  xinyuan-gas-backup weekly
  xinyuan-gas-backup verify-daily [snapshot-directory]
  xinyuan-gas-backup verify-weekly [archive-file]
  xinyuan-gas-backup restore-check [snapshot-directory]
  xinyuan-gas-backup status
EOF
}

validate_config
prepare_runtime
trap cleanup EXIT
check_free_space

case "${1:-}" in
  daily)
    run_daily
    ;;
  weekly)
    run_weekly
    ;;
  verify-daily)
    verify_daily "${2:-}"
    ;;
  verify-weekly)
    verify_weekly "${2:-}"
    ;;
  restore-check)
    restore_check "${2:-}"
    ;;
  status)
    show_status
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac

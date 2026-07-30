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

UPLOAD_ROOT="${UPLOAD_ROOT:-/var/www/xinyuan-gas/backend/storage/uploads}"
BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/xinyuan-gas}"
OFFSITE_REMOTE="${OFFSITE_REMOTE:-}"
OFFSITE_ENCRYPTED="${OFFSITE_ENCRYPTED:-false}"
RCLONE_CONFIG="${RCLONE_CONFIG:-/etc/xinyuan-gas/rclone.conf}"
LOG_FILE="${LOG_FILE:-/var/log/xinyuan-gas-backup.log}"
LOCK_FILE="/run/lock/xinyuan-gas-offsite-backup.lock"
STAMP="$(date +%Y%m%d_%H%M%S)"
export RCLONE_CONFIG

log() {
  printf '%s [%s] %s\n' "$(date --iso-8601=seconds)" "$$" "$*"
}

fail() {
  log "ERROR: $*"
  exit 1
}

[[ "$BACKUP_ROOT" == /var/backups/* ]] || fail "BACKUP_ROOT must stay under /var/backups"
[[ -d "$UPLOAD_ROOT" ]] || fail "Upload directory does not exist: $UPLOAD_ROOT"
[[ -n "$OFFSITE_REMOTE" ]] || fail "OFFSITE_REMOTE is not configured"
[[ "$OFFSITE_ENCRYPTED" == "true" ]] ||
  fail "OFFSITE_ENCRYPTED must be true; unencrypted offsite uploads are refused"
command -v rclone >/dev/null 2>&1 || fail "rclone is not installed"
[[ -r "$RCLONE_CONFIG" ]] || fail "rclone configuration is not readable: $RCLONE_CONFIG"

touch "$LOG_FILE"
chmod 0600 "$LOG_FILE"
exec > >(tee -a "$LOG_FILE") 2>&1
exec 9>"$LOCK_FILE"
flock -n 9 || fail "Another offsite backup process is already running"

latest_daily="$(<"$BACKUP_ROOT/state/latest-daily")"
[[ -f "$latest_daily/database.sql.gz" ]] || fail "Latest daily database backup is missing"

log "Copying daily database backup to encrypted offsite storage"
rclone copyto \
  "$latest_daily/database.sql.gz" \
  "$OFFSITE_REMOTE/daily-database/$(basename "$latest_daily").sql.gz" \
  --checksum --retries 3 --low-level-retries 10

log "Synchronizing changed uploads to encrypted offsite storage"
rclone sync \
  "$UPLOAD_ROOT/" \
  "$OFFSITE_REMOTE/uploads-current/" \
  --backup-dir "$OFFSITE_REMOTE/uploads-history/$STAMP/" \
  --checksum --create-empty-src-dirs --retries 3 --low-level-retries 10

if [[ -d "$BACKUP_ROOT/weekly" ]]; then
  log "Copying weekly full archives to encrypted offsite storage"
  rclone copy \
    "$BACKUP_ROOT/weekly/" \
    "$OFFSITE_REMOTE/weekly-full/" \
    --include 'xinyuan-gas-full-*.tar.gz' \
    --include 'xinyuan-gas-full-*.tar.gz.sha256' \
    --exclude '*' \
    --checksum --retries 3 --low-level-retries 10
fi

rclone lsf "$OFFSITE_REMOTE/daily-database/" --files-only | tail -n 5
printf '%s\n' "$(date --iso-8601=seconds)" > "$BACKUP_ROOT/state/offsite-last-success"
log "Encrypted offsite backup completed"

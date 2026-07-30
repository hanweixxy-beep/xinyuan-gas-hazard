#!/usr/bin/env bash
set -Eeuo pipefail

if (( EUID != 0 )); then
  echo "Run this installer as root, for example: sudo $0" >&2
  exit 1
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
CONFIG_DIR="/etc/xinyuan-gas"
SYSTEMD_DIR="/etc/systemd/system"

required_files=(
  "$REPO_ROOT/tools/xinyuan-gas-backup.sh"
  "$REPO_ROOT/tools/xinyuan-gas-offsite-backup.sh"
  "$REPO_ROOT/deploy/backup/xinyuan-gas-backup.env.example"
  "$REPO_ROOT/deploy/systemd/xinyuan-gas-backup-daily.service"
  "$REPO_ROOT/deploy/systemd/xinyuan-gas-backup-daily.timer"
  "$REPO_ROOT/deploy/systemd/xinyuan-gas-backup-weekly.service"
  "$REPO_ROOT/deploy/systemd/xinyuan-gas-backup-weekly.timer"
  "$REPO_ROOT/deploy/systemd/xinyuan-gas-backup-offsite.service"
  "$REPO_ROOT/deploy/systemd/xinyuan-gas-backup-offsite.timer"
)

for file in "${required_files[@]}"; do
  [[ -f "$file" ]] || {
    echo "Required file is missing: $file" >&2
    exit 1
  }
done

bash -n "$REPO_ROOT/tools/xinyuan-gas-backup.sh"
bash -n "$REPO_ROOT/tools/xinyuan-gas-offsite-backup.sh"

install -d -m 0750 "$CONFIG_DIR"
if [[ ! -e "$CONFIG_DIR/backup.env" ]]; then
  install -m 0600 \
    "$REPO_ROOT/deploy/backup/xinyuan-gas-backup.env.example" \
    "$CONFIG_DIR/backup.env"
  echo "Created $CONFIG_DIR/backup.env"
else
  echo "Preserved existing $CONFIG_DIR/backup.env"
fi

install -m 0750 \
  "$REPO_ROOT/tools/xinyuan-gas-backup.sh" \
  /usr/local/sbin/xinyuan-gas-backup
install -m 0750 \
  "$REPO_ROOT/tools/xinyuan-gas-offsite-backup.sh" \
  /usr/local/sbin/xinyuan-gas-offsite-backup

for unit in "$REPO_ROOT"/deploy/systemd/xinyuan-gas-backup-*; do
  install -m 0644 "$unit" "$SYSTEMD_DIR/$(basename "$unit")"
done

install -d -m 0700 /var/backups/xinyuan-gas
systemctl daemon-reload
systemctl enable --now \
  xinyuan-gas-backup-daily.timer \
  xinyuan-gas-backup-weekly.timer

cat <<'EOF'
Local automatic backups are installed.

Next:
  sudo /usr/local/sbin/xinyuan-gas-backup daily
  sudo /usr/local/sbin/xinyuan-gas-backup restore-check
  sudo /usr/local/sbin/xinyuan-gas-backup weekly
  sudo /usr/local/sbin/xinyuan-gas-backup status

The offsite timer remains disabled until an encrypted rclone remote is tested.
EOF

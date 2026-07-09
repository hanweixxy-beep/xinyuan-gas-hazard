#!/usr/bin/env bash
set -euo pipefail

ts="${1:?timestamp required}"
env_file="/var/www/xinyuan-gas/backend/.env"
backup_dir="/var/www/xinyuan-gas/.backup/import_${ts}"

set -a
source "$env_file"
set +a

mkdir -p "$backup_dir"
mysqldump --default-character-set=utf8mb4 \
  --no-tablespaces \
  -h "${DB_HOST:-127.0.0.1}" \
  -P "${DB_PORT:-3306}" \
  -u "${DB_USERNAME:-root}" \
  -p"${DB_PASSWORD:-}" \
  "${DB_DATABASE:-xinyuan_gas}" > "$backup_dir/database.sql"

echo "backup:$backup_dir/database.sql"

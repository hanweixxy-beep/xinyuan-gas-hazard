#!/usr/bin/env bash
set -euo pipefail

ts="${1:?timestamp required}"
root="/var/www/xinyuan-gas"
stage="/tmp/xinyuan_cloud_first_20260728"
backup_dir="$root/.backup/cloud_first_$ts"

files=(
  "backend/public/index.php"
  "backend/app/Http/Controllers/AuthController.php"
  "backend/app/Http/Controllers/RecordController.php"
  "backend/app/Services/AuthService.php"
  "backend/app/Services/AdminService.php"
  "backend/app/Services/RecordService.php"
  "backend/app/Repositories/UserRepository.php"
  "backend/app/Repositories/RecordRepository.php"
  "frontend/admin/index.html"
  "frontend/admin/app.js"
  "frontend/mobile/app.js"
)

for file in \
  "$stage/backend/public/index.php" \
  "$stage/backend/app/Http/Controllers/AuthController.php" \
  "$stage/backend/app/Http/Controllers/RecordController.php" \
  "$stage/backend/app/Services/AuthService.php" \
  "$stage/backend/app/Services/AdminService.php" \
  "$stage/backend/app/Services/RecordService.php" \
  "$stage/backend/app/Repositories/UserRepository.php" \
  "$stage/backend/app/Repositories/RecordRepository.php"
do
  php -l "$file"
done

sudo mkdir -p "$backup_dir"
sudo sh -c "mysqldump --single-transaction --routines --triggers xinyuan_gas | gzip -9 > '$backup_dir/xinyuan_gas_before.sql.gz'"
(
  cd "$root"
  sudo tar -czf "$backup_dir/files_before.tar.gz" "${files[@]}"
)

sudo mysql xinyuan_gas < "$stage/database/patches/20260728_cloud_first_accounts_drafts.sql"

for relative in "${files[@]}"; do
  sudo install -m 0644 "$stage/$relative" "$root/$relative"
  sudo chown www-data:www-data "$root/$relative"
done

sudo nginx -t

echo "backup:$backup_dir"
echo "schema:"
sudo mysql -Nse "
  SELECT CONCAT('users.login_scope=', COUNT(*))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA='xinyuan_gas' AND TABLE_NAME='users' AND COLUMN_NAME='login_scope';
  SELECT CONCAT('sessions.client_type=', COUNT(*))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA='xinyuan_gas' AND TABLE_NAME='sessions' AND COLUMN_NAME='client_type';
  SELECT CONCAT('records.record_state=', COUNT(*))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA='xinyuan_gas' AND TABLE_NAME='inspection_records' AND COLUMN_NAME='record_state';
  SELECT CONCAT('records=', COUNT(*)) FROM xinyuan_gas.inspection_records;
  SELECT CONCAT('users=', COUNT(*)) FROM xinyuan_gas.users;
"
curl -fsS https://www.gashbxy.com/api/health
echo

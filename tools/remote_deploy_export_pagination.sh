#!/usr/bin/env bash
set -euo pipefail

ts="${1:?timestamp required}"
root="/var/www/xinyuan-gas"
backup_dir="$root/.backup/export_pagination_$ts"

php -l /tmp/ExportService.php
php -l /tmp/ExportController.php

mkdir -p \
  "$backup_dir/backend/app/Services" \
  "$backup_dir/backend/app/Http/Controllers" \
  "$backup_dir/frontend/admin"

cp "$root/backend/app/Services/ExportService.php" "$backup_dir/backend/app/Services/ExportService.php"
cp "$root/backend/app/Http/Controllers/ExportController.php" "$backup_dir/backend/app/Http/Controllers/ExportController.php"
cp "$root/frontend/admin/app.js" "$backup_dir/frontend/admin/app.js"
cp "$root/frontend/admin/styles.css" "$backup_dir/frontend/admin/styles.css"
cp "$root/frontend/admin/index.html" "$backup_dir/frontend/admin/index.html"

install -m 0644 /tmp/ExportService.php "$root/backend/app/Services/ExportService.php"
install -m 0644 /tmp/ExportController.php "$root/backend/app/Http/Controllers/ExportController.php"
install -m 0644 /tmp/app.js "$root/frontend/admin/app.js"
install -m 0644 /tmp/styles.css "$root/frontend/admin/styles.css"
install -m 0644 /tmp/index.html "$root/frontend/admin/index.html"

chown www-data:www-data \
  "$root/backend/app/Services/ExportService.php" \
  "$root/backend/app/Http/Controllers/ExportController.php" \
  "$root/frontend/admin/app.js" \
  "$root/frontend/admin/styles.css" \
  "$root/frontend/admin/index.html"

echo "backup:$backup_dir"

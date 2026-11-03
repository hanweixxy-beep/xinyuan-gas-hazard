#!/usr/bin/env bash
set -euo pipefail
stage="$(cd "$(dirname "$0")/.." && pwd)"
live=/var/www/xinyuan-gas
unit=/etc/systemd/system/xinyuan-export-worker.service
export_root=/var/lib/xinyuan-gas-exports
cd "$live"
sha256sum -c "$stage/deployment-baseline.sha256"
(cd "$stage" && sha256sum -c deployment-current.sha256)
mapfile -t existing < "$stage/deployment-existing.txt"
mapfile -t added < "$stage/deployment-added.txt"
for file in "${added[@]}"; do
  [[ ! -e "$live/$file" ]] || { echo "Unexpected existing file: $file" >&2; exit 1; }
done
[[ ! -e "$unit" ]]
EXPORT_STAGE="$stage" php <<'PHP'
<?php
require '/var/www/xinyuan-gas/backend/app/Core/Autoloader.php';
App\Core\Autoloader::register('/var/www/xinyuan-gas/backend/app');
App\Core\Env::load('/var/www/xinyuan-gas/backend/.env');
$config = require getenv('EXPORT_STAGE') . '/backend/config/app.php';
if ($config['export_root'] !== '/var/lib/xinyuan-gas-exports') throw new RuntimeException('Unexpected export storage configuration.');
if (!is_dir($config['upload_root'])) throw new RuntimeException('Upload root is not available.');
PHP
for extension in gd exif zip pdo_mysql; do php -m | grep -Fx "$extension" >/dev/null; done
for file in "${existing[@]}" "${added[@]}"; do
  if [[ "$file" == *.php ]]; then php -l "$stage/$file"; fi
done
nginx -t
systemd-analyze verify "$stage/deploy/xinyuan-export-worker.service"
backup="/var/backups/xinyuan/releases/code-pre-export-jobs-$(date +%Y%m%d_%H%M%S).tgz"
mkdir -p /var/backups/xinyuan/releases
tar -czf "$backup" "${existing[@]}"
tar -tzf "$backup" >/dev/null
rollback() {
  trap - ERR
  systemctl disable --now xinyuan-export-worker.service 2>/dev/null || true
  rm -f -- "$unit"
  systemctl daemon-reload
  tar -xzf "$backup" -C "$live"
  for file in "${added[@]}"; do rm -f -- "$live/$file"; done
  systemctl reload php8.3-fpm
  echo "Deployment failed; restored $backup. Export artifacts retained at $export_root." >&2
}
trap rollback ERR
install -d -o www-data -g www-data -m 0700 "$export_root"
install -o www-data -g www-data -m 0644 "$stage/backend/config/app.php" "$live/backend/config/app.php.export-new"
mv -f -- "$live/backend/config/app.php.export-new" "$live/backend/config/app.php"
for file in "${added[@]}" "${existing[@]}"; do
  install -d -o www-data -g www-data -m 0755 "$(dirname "$live/$file")"
  install -o www-data -g www-data -m 0644 "$stage/$file" "$live/$file.export-new"
  mv -f -- "$live/$file.export-new" "$live/$file"
done
sha256sum -c "$stage/deployment-current.sha256"
install -o root -g root -m 0644 "$stage/deploy/xinyuan-export-worker.service" "$unit"
systemctl daemon-reload
systemctl enable --now xinyuan-export-worker.service
systemctl reload php8.3-fpm
sleep 3
systemctl is-active --quiet xinyuan-export-worker.service php8.3-fpm nginx
curl --fail --silent --show-error --max-time 20 https://www.gashbxy.com/api/health
curl --fail --silent --show-error --max-time 20 https://www.gashbxy.com/admin/ -o "$stage/admin-live.html"
grep -q '20261019-photo-export' "$stage/admin-live.html"
grep -q 'exportJobsList' "$stage/admin-live.html"
for asset in app.js exportJobs.js redesign.css; do
  curl --fail --silent --show-error --max-time 20 "https://www.gashbxy.com/admin/$asset" -o "$stage/live-$asset"
  cmp "$stage/frontend/admin/$asset" "$stage/live-$asset"
done
for route in 'GET export/jobs' 'POST export/jobs' 'POST export/jobs/1/cancel' 'POST export/jobs/1/files/1/ticket' 'GET records' 'GET export/xlsx'; do
  read -r method endpoint <<< "$route"
  code="$(curl --silent --show-error --max-time 20 -X "$method" -o /dev/null -w '%{http_code}' "https://www.gashbxy.com/api/$endpoint")"
  [[ "$code" == 401 ]]
done
code="$(curl --silent --show-error --max-time 20 -o /dev/null -w '%{http_code}' 'https://www.gashbxy.com/api/export/jobs/1/files/1?ticket=invalid')"
[[ "$code" == 403 ]]
trap - ERR
echo
echo "DEPLOY_OK backup=$backup"
echo "WORKER=$(systemctl is-active xinyuan-export-worker.service)"

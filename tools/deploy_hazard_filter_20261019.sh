#!/usr/bin/env bash
set -euo pipefail
stage="$(cd "$(dirname "$0")/.." && pwd)"
live=/var/www/xinyuan-gas
files=(
  backend/app/Http/Controllers/RecordController.php
  backend/app/Http/Controllers/ExportController.php
  backend/app/Repositories/RecordRepository.php
  frontend/admin/app.js
  frontend/admin/index.html
)
cd "$live"
sha256sum -c "$stage/deployment-baseline.sha256"
(cd "$stage" && sha256sum -c deployment-current.sha256)
for file in "${files[@]}"; do
  if [[ "$file" == *.php ]]; then php -l "$stage/$file"; fi
done
nginx -t
backup="/var/backups/xinyuan/releases/code-pre-hazard-filter-$(date +%Y%m%d_%H%M%S).tgz"
mkdir -p /var/backups/xinyuan/releases
tar -czf "$backup" "${files[@]}"
tar -tzf "$backup" >/dev/null
rollback() {
  trap - ERR
  tar -xzf "$backup" -C "$live"
  systemctl reload php8.3-fpm
  echo "Deployment failed; restored $backup" >&2
}
trap rollback ERR
for file in "${files[@]}"; do
  install -o www-data -g www-data -m 0644 "$stage/$file" "$live/$file.hazard-new"
  mv -f -- "$live/$file.hazard-new" "$live/$file"
done
sha256sum -c "$stage/deployment-current.sha256"
systemctl reload php8.3-fpm
systemctl is-active --quiet php8.3-fpm nginx
curl --fail --silent --show-error --max-time 20 https://www.gashbxy.com/api/health
curl --fail --silent --show-error --max-time 20 https://www.gashbxy.com/admin/ -o "$stage/admin-live.html"
grep -q '20261019-hazard-filter' "$stage/admin-live.html"
grep -q 'filterHazardSelect' "$stage/admin-live.html"
for endpoint in records export/xlsx; do
  status="$(curl --silent --show-error --max-time 20 -o /dev/null -w '%{http_code}' "https://www.gashbxy.com/api/$endpoint?hazardCode=alarm_missing")"
  [[ "$status" == 401 ]]
done
trap - ERR
echo
echo "DEPLOY_OK backup=$backup"

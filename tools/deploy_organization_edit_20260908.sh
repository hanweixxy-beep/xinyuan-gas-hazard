#!/usr/bin/env bash
set -euo pipefail

stage="$(cd "$(dirname "$0")/.." && pwd)"
live=/var/www/xinyuan-gas
files=(
  backend/app/Http/Controllers/AdminController.php
  backend/app/Repositories/OrganizationRepository.php
  backend/app/Repositories/RecordRepository.php
  backend/app/Services/AdminService.php
  backend/public/index.php
  frontend/admin/app.js
  frontend/admin/index.html
  frontend/admin/redesign.css
)

cd "$live"
sha256sum -c "$stage/deployment-baseline.sha256"
for file in "${files[@]}"; do
  if [[ "$file" == *.php ]]; then php -l "$stage/$file"; fi
done
backup="/var/backups/xinyuan/releases/code-pre-organization-edit-$(date +%Y%m%d_%H%M%S).tgz"
mkdir -p /var/backups/xinyuan/releases
tar -czf "$backup" "${files[@]}"
tar -tzf "$backup" >/dev/null
rollback() {
  tar -xzf "$backup" -C "$live"
  systemctl reload php8.3-fpm
  echo "Deployment failed; restored $backup" >&2
}
trap rollback ERR
for file in "${files[@]}"; do
  install -o www-data -g www-data -m 0644 "$stage/$file" "$live/$file"
  cmp "$stage/$file" "$live/$file"
done
nginx -t
systemctl reload php8.3-fpm
curl --fail --silent --show-error --max-time 20 https://www.gashbxy.com/api/health
curl --fail --silent --show-error --max-time 20 https://www.gashbxy.com/admin/ | grep -q '20260908-organization-edit'
for type in regions stores; do
  status="$(curl --silent --show-error --max-time 20 -X POST -o /dev/null -w '%{http_code}' "https://www.gashbxy.com/api/admin/$type/0")"
  [[ "$status" == 401 ]]
done
trap - ERR
echo
echo "DEPLOY_OK backup=$backup"

#!/usr/bin/env bash
set -euo pipefail
stage="$(cd "$(dirname "$0")/.." && pwd)"
live=/var/www/xinyuan-gas
existing=(
  backend/app/Http/Controllers/AdminController.php
  backend/app/Repositories/UserRepository.php
  backend/app/Services/AdminService.php
  backend/public/index.php
  frontend/admin/app.js
  frontend/admin/index.html
  frontend/admin/redesign.css
)
added=(
  backend/app/Services/UserBatchService.php
  frontend/admin/userWorkbook.js
  frontend/admin/userImportWorker.js
  frontend/admin/vendor/xlsx-0.20.3.min.js
  frontend/admin/vendor/SHEETJS-LICENSE.txt
  frontend/admin/vendor/README.md
)
cd "$live"
sha256sum -c "$stage/deployment-baseline.sha256"
for file in "${added[@]}"; do
  if [[ -e "$live/$file" ]]; then echo "Unexpected existing file: $file" >&2; exit 1; fi
done
(cd "$stage" && sha256sum -c deployment-current.sha256)
for file in "${existing[@]}" "${added[@]}"; do
  if [[ "$file" == *.php ]]; then php -l "$stage/$file"; fi
done
nginx -t
backup="/var/backups/xinyuan/releases/code-pre-user-batch-$(date +%Y%m%d_%H%M%S).tgz"
mkdir -p /var/backups/xinyuan/releases
tar -czf "$backup" "${existing[@]}"
tar -tzf "$backup" >/dev/null
rollback() {
  trap - ERR
  tar -xzf "$backup" -C "$live"
  for file in "${added[@]}"; do rm -f -- "$live/$file"; done
  systemctl reload php8.3-fpm
  echo "Deployment failed; restored $backup" >&2
}
trap rollback ERR
for file in "${added[@]}" "${existing[@]}"; do
  install -d -o www-data -g www-data -m 0755 "$(dirname "$live/$file")"
  install -o www-data -g www-data -m 0644 "$stage/$file" "$live/$file.user-batch-new"
  mv -f -- "$live/$file.user-batch-new" "$live/$file"
done
sha256sum -c "$stage/deployment-current.sha256"
systemctl reload php8.3-fpm
systemctl is-active --quiet php8.3-fpm nginx
curl --fail --silent --show-error --max-time 20 https://www.gashbxy.com/api/health
curl --fail --silent --show-error --max-time 20 https://www.gashbxy.com/admin/ | grep -q '20260915-user-batch'
for asset in userWorkbook.js userImportWorker.js vendor/xlsx-0.20.3.min.js; do
  curl --fail --silent --show-error --max-time 20 "https://www.gashbxy.com/admin/$asset" -o /dev/null
done
for endpoint in import-options import-preview import; do
  method=POST
  [[ "$endpoint" != import-options ]] || method=GET
  status="$(curl --silent --show-error --max-time 20 -X "$method" -o /dev/null -w '%{http_code}' "https://www.gashbxy.com/api/admin/users/$endpoint")"
  [[ "$status" == 401 ]]
done
trap - ERR
echo
echo "DEPLOY_OK backup=$backup"

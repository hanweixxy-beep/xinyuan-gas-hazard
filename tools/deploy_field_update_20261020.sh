#!/usr/bin/env bash
set -euo pipefail
stage="$(cd "$(dirname "$0")/.." && pwd)"
live=/var/www/xinyuan-gas
cd "$live"
sha256sum -c "$stage/deployment-baseline.sha256"
(cd "$stage" && sha256sum -c deployment-current.sha256)
mapfile -t existing < "$stage/deployment-existing.txt"
mapfile -t added < "$stage/deployment-added.txt"
for file in "${added[@]}"; do [[ ! -e "$live/$file" ]]; done
for file in "${existing[@]}" "${added[@]}"; do
  if [[ "$file" == *.php ]]; then php -l "$stage/$file"; fi
done
nginx -t
backup="/var/backups/xinyuan/releases/code-pre-field-update-$(date +%Y%m%d_%H%M%S).tgz"
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
  install -o www-data -g www-data -m 0644 "$stage/$file" "$live/$file.field-new"
  mv -f -- "$live/$file.field-new" "$live/$file"
done
sha256sum -c "$stage/deployment-current.sha256"
systemctl reload php8.3-fpm
# The worker consumes fixed ID snapshots; its processing code is unchanged.
# Keep it running so an existing photo export is not interrupted.
systemctl is-active --quiet php8.3-fpm nginx xinyuan-export-worker.service
curl --fail --silent --show-error --max-time 20 https://www.gashbxy.com/api/health
for panel in admin mobile; do
  curl --fail --silent --show-error --max-time 20 "https://www.gashbxy.com/$panel/" -o "$stage/live-$panel.html"
  grep -q '20261020-field-update\|20261020-photo-gas-multifilter' "$stage/live-$panel.html"
  cmp "$stage/frontend/$panel/index.html" "$stage/live-$panel.html"
done
for asset in shared/catalog.js admin/app.js admin/redesign.css admin/exportJobs.js mobile/app.js; do
  curl --fail --silent --show-error --max-time 20 "https://www.gashbxy.com/$asset" -o "$stage/live-$(basename "$asset")-$(dirname "$asset" | tr / _)"
  file="$asset"
  [[ "$asset" == shared/* ]] || file="frontend/$asset"
  cmp "$stage/$file" "$stage/live-$(basename "$asset")-$(dirname "$asset" | tr / _)"
done
curl --fail --silent --show-error --max-time 20 https://www.gashbxy.com/api/catalog -o "$stage/live-catalog.json"
grep -q '虚假安检' "$stage/live-catalog.json"
for route in 'GET records' 'GET export/xlsx' 'GET export/jobs' 'POST export/jobs' 'POST records/drafts' 'POST records/drafts/submit' 'POST records/1/rectify'; do
  read -r method endpoint <<< "$route"
  code="$(curl --silent --show-error --max-time 20 -X "$method" -o /dev/null -w '%{http_code}' "https://www.gashbxy.com/api/$endpoint")"
  [[ "$code" == 401 ]]
done
trap - ERR
echo
echo "DEPLOY_OK backup=$backup"

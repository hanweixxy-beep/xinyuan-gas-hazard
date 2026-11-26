#!/usr/bin/env bash
set -Eeuo pipefail
archive="$1"
checksum="$2"
[[ "$archive" =~ ^/var/tmp/xinyuan-photo-supplement-[a-f0-9]{32}\.tgz$ && -f "$archive" && ! -L "$archive" ]]
[[ "$checksum" =~ ^[a-f0-9]{64}$ ]]
live=/var/www/xinyuan-gas
url=https://www.gashbxy.com
version=20261125-photo-supplement
files=(backend/app/Repositories/RecordRepository.php backend/app/Services/RecordService.php
  backend/app/Http/Controllers/RecordController.php backend/public/index.php
  frontend/mobile/app.js frontend/mobile/index.html frontend/mobile/styles.css)
printf '%s  %s\n' "$checksum" "$archive" | sha256sum -c -
diff -u <(printf '%s\n' "${files[@]}" baseline.sha256 current.sha256 | LC_ALL=C sort) <(tar -tzf "$archive" | LC_ALL=C sort)
stage="$(mktemp -d /var/tmp/xinyuan-photo-supplement.XXXXXXXX)"
trap '[[ "$stage" =~ ^/var/tmp/xinyuan-photo-supplement\.[a-zA-Z0-9]+$ ]] && rm -rf -- "$stage"' EXIT
tar -xzf "$archive" -C "$stage" --no-same-owner --no-same-permissions
for file in "${files[@]}" baseline.sha256 current.sha256; do [[ -f "$stage/$file" && ! -L "$stage/$file" ]]; done
(cd "$stage" && sha256sum --strict -c current.sha256)
cd "$live"
for file in "${files[@]}"; do [[ -f "$file" && ! -L "$file" ]]; done
sha256sum --strict -c "$stage/baseline.sha256"
for file in "${files[@]:0:4}"; do php -l "$stage/$file"; done
mysql -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='xinyuan_gas' AND table_name='audit_logs'" | grep -qx 1
nginx -t
systemctl is-active --quiet php8.3-fpm
systemctl is-active --quiet nginx
backup="/var/backups/xinyuan/releases/code-pre-photo-supplement-$(date +%Y%m%d_%H%M%S)-$$.tgz"
tar -czf "$backup" "${files[@]}"
tar -tzf "$backup" >/dev/null
rollback() {
  local failed="$1"
  trap - ERR
  set +e
  tar -xzf "$backup" -C "$live"
  for file in "${files[@]}"; do rm -f -- "$file.photo-new-$$"; done
  systemctl reload php8.3-fpm
  sha256sum --strict -c "$stage/baseline.sha256"
  echo "Photo supplement release rolled back: $backup" >&2
  exit "$failed"
}
trap 'rollback "$?"' ERR
for file in "${files[@]}"; do
  install -o "$(stat -c %u "$file")" -g "$(stat -c %g "$file")" -m "$(stat -c %a "$file")" "$stage/$file" "$file.photo-new-$$"
done
sha256sum --strict -c "$stage/baseline.sha256"
for file in "${files[@]}"; do mv -f -- "$file.photo-new-$$" "$file"; done
sha256sum --strict -c "$stage/current.sha256"
systemctl reload php8.3-fpm
systemctl is-active --quiet php8.3-fpm
systemctl is-active --quiet nginx
curl --fail --silent --show-error --max-time 20 "$url/api/health" -o "$stage/health.json"
php -r '$r=json_decode(file_get_contents($argv[1]),true,512,JSON_THROW_ON_ERROR); if(($r["ok"]??false)!==true||($r["data"]["status"]??"")!=="ok")exit(1);' "$stage/health.json"
for file in index.html app.js styles.css; do
  asset="$file"; [[ "$file" != index.html ]] || asset=""
  curl --fail --silent --show-error --max-time 20 -H 'Cache-Control: no-cache' "$url/mobile/$asset?v=$version" -o "$stage/live-$file"
  cmp "$stage/frontend/mobile/$file" "$stage/live-$file"
done
status="$(curl --silent --show-error --max-time 20 -o /dev/null -w '%{http_code}' -X POST "$url/api/records/0/rectification-photo")"
[[ "$status" == 401 ]]
status="$(curl --silent --show-error --max-time 20 -o /dev/null -w '%{http_code}' "$url/api/records")"
[[ "$status" == 401 ]]
trap - ERR
printf 'PHOTO_SUPPLEMENT_DEPLOYED version=%s backup=%s\n' "$version" "$backup"

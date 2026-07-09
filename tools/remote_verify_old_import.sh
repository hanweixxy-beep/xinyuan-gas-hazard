#!/usr/bin/env bash
set -euo pipefail

env_file="/var/www/xinyuan-gas/backend/.env"
set -a
source "$env_file"
set +a

mysql_cmd=(
  mysql
  --default-character-set=utf8mb4
  -h "${DB_HOST:-127.0.0.1}"
  -P "${DB_PORT:-3306}"
  -u "${DB_USERNAME:-root}"
  -p"${DB_PASSWORD:-}"
  -N
  -B
  "${DB_DATABASE:-xinyuan_gas}"
)

echo "records_by_company"
"${mysql_cmd[@]}" -e "
SELECT c.id, c.name, COUNT(*)
FROM inspection_records r
JOIN companies c ON c.id = r.company_id
WHERE r.client_uuid LIKE 'legacy-xlsx-%'
GROUP BY c.id, c.name
ORDER BY c.id;
"

echo "records_by_result"
"${mysql_cmd[@]}" -e "
SELECT r.check_result, r.status, COUNT(*)
FROM inspection_records r
WHERE r.client_uuid LIKE 'legacy-xlsx-%'
GROUP BY r.check_result, r.status
ORDER BY r.check_result, r.status;
"

echo "photos_by_type"
"${mysql_cmd[@]}" -e "
SELECT p.photo_type, COUNT(*)
FROM record_photos p
JOIN inspection_records r ON r.id = p.record_id
WHERE r.client_uuid LIKE 'legacy-xlsx-%'
GROUP BY p.photo_type
ORDER BY p.photo_type;
"

echo "phone_one"
"${mysql_cmd[@]}" -e "
SELECT id, customer_name, customer_phone
FROM inspection_records
WHERE client_uuid LIKE 'legacy-xlsx-%' AND customer_phone = '1';
"

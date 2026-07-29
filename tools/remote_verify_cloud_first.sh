#!/usr/bin/env bash
set -euo pipefail

db="xinyuan_gas"
base_url="https://www.gashbxy.com/api"
client_uuid="codex-cloud-first-test-$(date +%s)"
invalid_uuid="${client_uuid}-invalid"
token="$(openssl rand -hex 32)"
token_hash="$(printf '%s' "$token" | sha256sum | cut -d' ' -f1)"

user_id="$(sudo mysql -Nse "
  SELECT id FROM ${db}.users
  WHERE role='inspector' AND status='active' AND company_id IS NOT NULL
  ORDER BY id ASC LIMIT 1
")"

if [[ -z "$user_id" ]]; then
  echo "No active inspector account is available for verification." >&2
  exit 1
fi

cleanup() {
  sudo mysql -e "
    DELETE FROM ${db}.inspection_records
    WHERE client_uuid IN ('${client_uuid}', '${invalid_uuid}');
    DELETE FROM ${db}.sessions WHERE token_hash='${token_hash}';
  " >/dev/null
}
trap cleanup EXIT

sudo mysql -e "
  INSERT INTO ${db}.sessions (user_id, client_type, token_hash, expires_at)
  VALUES (${user_id}, 'app', '${token_hash}', DATE_ADD(NOW(), INTERVAL 10 MINUTE));
"

db_count="$(sudo mysql -Nse "
  SELECT COUNT(*) FROM ${db}.inspection_records
  WHERE creator_id=${user_id} AND record_state='submitted'
")"
api_count="$(curl -fsS -H "Authorization: Bearer ${token}" "${base_url}/records" |
  php -r '$v=json_decode(stream_get_contents(STDIN), true); echo count($v["data"]["records"] ?? []);')"

if [[ "$db_count" != "$api_count" ]]; then
  echo "Personal record scope mismatch: db=${db_count}, api=${api_count}" >&2
  exit 1
fi

payload="$(cat <<JSON
{
  "clientUuid": "${client_uuid}",
  "customerName": "系统联调测试",
  "customerPhone": "00000000000",
  "customerType": "餐饮用户",
  "customerAddress": "自动测试地址",
  "inspectorName": "系统联调",
  "deliveryPerson": "系统联调",
  "inspectionDate": "2026-07-28",
  "checkResult": "隐患已清零",
  "remark": "验证后自动清理",
  "hazardItems": [],
  "gasSourceIssues": [],
  "photos": {
    "storefront": ["/uploads/codex-verification.jpg"],
    "hazard": {},
    "cylinder_qr": [],
    "cylinder": [],
    "rectification": []
  }
}
JSON
)"

draft_id="$(curl -fsS \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d "$payload" \
  "${base_url}/records/drafts" |
  php -r '$v=json_decode(stream_get_contents(STDIN), true); echo (int)($v["data"]["id"] ?? 0);')"

submit_id="$(curl -fsS \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d "$payload" \
  "${base_url}/records/drafts/submit" |
  php -r '$v=json_decode(stream_get_contents(STDIN), true); echo (int)($v["data"]["id"] ?? 0);')"

state="$(sudo mysql -Nse "
  SELECT CONCAT(record_state, ':', creator_id)
  FROM ${db}.inspection_records
  WHERE client_uuid='${client_uuid}'
")"

if [[ "$draft_id" -le 0 || "$submit_id" != "$draft_id" || "$state" != "submitted:${user_id}" ]]; then
  echo "Draft submission verification failed." >&2
  exit 1
fi

invalid_payload="$(printf '%s' "$payload" | php -r '
  $payload = json_decode(stream_get_contents(STDIN), true);
  $payload["clientUuid"] .= "-invalid";
  $payload["photos"]["storefront"] = [];
  echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
')"
invalid_status="$(curl -sS -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d "$invalid_payload" \
  "${base_url}/records/drafts/submit")"

if [[ "$invalid_status" != "422" ]]; then
  echo "Missing-photo validation returned HTTP ${invalid_status}, expected 422." >&2
  exit 1
fi

echo "personal_scope=ok count=${api_count}"
echo "draft_upsert=ok id=${draft_id}"
echo "draft_submit=ok"
echo "missing_photo_validation=ok"

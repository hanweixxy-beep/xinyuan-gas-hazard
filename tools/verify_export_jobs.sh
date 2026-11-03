#!/usr/bin/env bash
set -euo pipefail
code_root="$(cd "$(dirname "$0")/.." && pwd)"
test_db="xinyuan_test_exports_$(date +%s)_$$"
cleanup() { mysql -e "DROP DATABASE IF EXISTS \`$test_db\`"; }
trap cleanup EXIT
find "$code_root/backend/app" "$code_root/backend/tests" "$code_root/backend/bin" -name '*.php' -print0 | xargs -0 -n1 php -l >/dev/null
php -l "$code_root/backend/public/index.php"
mysql -e "CREATE DATABASE \`$test_db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mysqldump --no-data --skip-comments --no-tablespaces xinyuan_gas | mysql "$test_db"
php -d memory_limit=512M "$code_root/backend/tests/HazardFilterIntegrationTest.php" "$test_db"
php -d memory_limit=512M "$code_root/backend/tests/ExportJobIntegrationTest.php" "$test_db"
php "$code_root/backend/tests/OrganizationIntegrationTest.php" "$test_db"
php "$code_root/backend/tests/UserAccessTest.php"
php "$code_root/backend/tests/ExportOrganizationColumnsTest.php"
echo 'Isolated background export and regression tests passed.'

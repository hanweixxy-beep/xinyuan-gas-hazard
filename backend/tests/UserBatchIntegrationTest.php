<?php

use App\Core\Autoloader;
use App\Core\HttpException;
use App\Repositories\CompanyRepository;
use App\Repositories\OrganizationRepository;
use App\Repositories\UserRepository;
use App\Services\AdminService;
use App\Services\UserBatchService;

require dirname(__DIR__) . '/app/Core/Autoloader.php';
Autoloader::register(dirname(__DIR__) . '/app');
$database = (string) ($argv[1] ?? '');
if (!preg_match('/^xinyuan_test_[a-zA-Z0-9_]+$/', $database)) {
    throw new RuntimeException('Use an isolated database starting with xinyuan_test_.');
}
$pdo = new PDO("mysql:unix_socket=/var/run/mysqld/mysqld.sock;dbname={$database};charset=utf8mb4", 'root', '', [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => false,
]);
$companies = new CompanyRepository($pdo);
$users = new UserRepository($pdo);
$admin = new AdminService($companies, new OrganizationRepository($pdo), $users);
$batch = new UserBatchService($admin, $users);
$companyA = $companies->create('批量测试甲公司', 'BATCH_A');
$companyB = $companies->create('批量测试乙公司', 'BATCH_B');
$companyDisabled = $companies->create('批量测试停用公司', 'BATCH_DISABLED');
$pdo->prepare("UPDATE companies SET status = 'disabled' WHERE id = ?")->execute([$companyDisabled]);
$hq = ['id' => 0, 'role' => 'hq_admin', 'company_id' => $companyA];
$manager = ['id' => 0, 'role' => 'company_admin', 'company_id' => $companyA];
$inspector = ['id' => 0, 'role' => 'inspector', 'company_id' => $companyA];
$checks = 0;
function check(bool $condition, string $message): void
{
    global $checks;
    if (!$condition) throw new RuntimeException($message);
    $checks++;
}
function rejected(callable $callback, int $status = 422): void
{
    try { $callback(); } catch (HttpException $error) {
        check($error->status() === $status, 'Unexpected HTTP rejection status.');
        check(!str_contains($error->getMessage(), 'Fixture-Secret'), 'A password leaked in an error.');
        return;
    }
    throw new RuntimeException('Expected request to be rejected.');
}
function row(int $number, array $changes = []): array
{
    return array_merge([
        'rowNumber' => $number, 'companyCode' => 'BATCH_A', 'name' => '  测试用户' . $number . '  ',
        'phone' => '09888000' . str_pad((string) $number, 3, '0', STR_PAD_LEFT),
        'role' => '', 'loginScope' => '', 'canViewCompanyRecords' => '', 'password' => 'Fixture-Secret-123',
    ], $changes);
}
$count = fn (): int => (int) $pdo->query('SELECT COUNT(*) FROM users')->fetchColumn();
$start = $count();
$options = $batch->options($manager);
check(count($options['fields']) === 7 && $options['maxRows'] === 200, 'Template contract changed.');
check(array_column($options['companies'], 'code') === ['BATCH_A'], 'Company options escaped actor scope.');
check($options['roles'] === ['检查员'], 'Company manager can assign an elevated role.');
check(!in_array('BATCH_DISABLED', array_column($batch->options($hq)['companies'], 'code'), true), 'Disabled company in template.');
rejected(fn () => $batch->options($inspector), 403);
rejected(fn () => $batch->preview($inspector, [row(2)]), 403);
rejected(fn () => $batch->create($inspector, [row(2)]), 403);
rejected(fn () => $admin->users($inspector), 403);

$valid = [row(2), row(3, ['canViewCompanyRecords' => '是', 'loginScope' => '仅APP'])];
$preview = $batch->preview($manager, $valid);
check($preview['total'] === 2 && $preview['validCount'] === 2 && $preview['errorCount'] === 0, 'Valid preview failed.');
check($count() === $start, 'Preview created accounts.');
$previewJson = json_encode($preview);
check(!str_contains($previewJson, 'password') && !str_contains($previewJson, 'Fixture-Secret'), 'Preview exposes a password.');
check($preview['rows'][0]['phone'] === $valid[0]['phone'], 'Leading zero in phone lost.');
check($preview['rows'][0]['role'] === '检查员' && $preview['rows'][0]['loginScope'] === 'APP+后台', 'Defaults incorrect.');
check($preview['rows'][0]['canViewCompanyRecords'] === '是', 'Default inspector company collaboration must be visible in preview.');
check($batch->preview($manager, [row(2, ['canViewCompanyRecords' => '否'])])['rows'][0]['canViewCompanyRecords'] === '是', 'Legacy no flag must normalize to company collaboration.');

$invalid = [row(2), row(3, ['name' => ''])];
check($batch->preview($hq, $invalid)['errorCount'] === 1, 'Missing name not flagged.');
rejected(fn () => $batch->create($hq, $invalid));
check($count() === $start, 'Invalid batch was partially inserted.');
$duplicate = [row(2), row(3, ['phone' => row(2)['phone']])];
check($batch->preview($hq, $duplicate)['errorCount'] === 2, 'Both duplicate rows must be flagged.');
rejected(fn () => $batch->create($hq, $duplicate));
foreach ([
    ['companyCode' => 'BATCH_B'], ['companyCode' => 'BATCH_DISABLED'], ['companyCode' => 'UNKNOWN'],
    ['role' => '总部管理员'], ['role' => '公司管理员'], ['role' => 'invalid'], ['loginScope' => 'invalid'],
    ['canViewCompanyRecords' => '任意'], ['phone' => 'not-a-phone'], ['password' => 'short'],
    ['password' => str_repeat(' ', 8)], ['password' => str_repeat('a', 73)], ['password' => "invalid\0password"],
] as $invalidFields) {
    $rows = [row(2, $invalidFields)];
    check($batch->preview($manager, $rows)['errorCount'] === 1, 'Invalid or privileged data accepted.');
    rejected(fn () => $batch->create($manager, $rows));
}
check($count() === $start, 'Rejected data changed user count.');
rejected(fn () => $batch->create($hq, []));
rejected(fn () => $batch->preview($hq, array_fill(0, 201, row(2))));
rejected(fn () => $batch->preview($hq, [row(1)]));
rejected(fn () => $batch->preview($hq, [row(202)]));
rejected(fn () => $batch->preview($hq, [row(2), row(2)]));
rejected(fn () => $batch->preview($hq, [row(2, ['phone' => 12345678])]));
rejected(fn () => $batch->preview($hq, [row(2, ['name' => str_repeat('x', 2001)])]));
check($batch->preview($hq, array_map(fn ($i) => row($i), range(2, 201)))['validCount'] === 200, '200-row batch limit failed.');

$result = $batch->create($manager, $valid);
check($result === ['createdCount' => 2] && $count() === $start + 2, 'Atomic valid creation failed.');
$created = $users->findByPhone(row(2)['phone']);
check($created['name'] === '测试用户2' && $created['phone'] === row(2)['phone'], 'Text normalization failed.');
check(password_verify('Fixture-Secret-123', $created['password_hash']), 'Initial password hash is invalid.');
check($created['region_id'] === null && $created['store_id'] === null && $created['role'] === 'inspector', 'Account organization or role incorrect.');
check((int)$created['can_view_company_records'] === 1, 'Default imported inspector must receive company collaboration.');
$otherCreated = $users->findByPhone(row(3)['phone']);
check((int) $otherCreated['can_view_company_records'] === 1 && $otherCreated['login_scope'] === 'app', 'Inspector permissions not retained.');
rejected(fn () => $batch->create($manager, $valid));
check($count() === $start + 2, 'Replay created duplicates.');
$pdo->prepare("UPDATE users SET status = 'disabled' WHERE id = ?")->execute([$created['id']]);
check($batch->preview($hq, [row(2)])['errorCount'] === 1, 'Disabled duplicate account ignored.');

// Simulate a duplicate arriving after validation: the earlier insert must roll back too.
$prepared = $admin->validateNewUser($hq, [
    'companyId' => $companyA, 'name' => '并发回滚测试', 'phone' => row(4)['phone'], 'password' => 'Fixture-Secret-123', 'role' => 'inspector',
]);
$prepared['password_hash'] = password_hash($prepared['password'], PASSWORD_DEFAULT);
unset($prepared['password']);
$prepared['rowNumber'] = 2;
rejected(fn () => $users->createUsersAtomically([$prepared, array_merge($prepared, ['rowNumber' => 3, 'phone' => row(2)['phone']])]), 409);
check(!$users->findByPhone(row(4)['phone']) && $count() === $start + 2 && !$pdo->inTransaction(), 'Concurrent collision left a partial batch.');

check($batch->create($hq, [row(4, ['companyCode' => 'BATCH_B', 'role' => '公司管理员']), row(5, ['role' => '总部管理员'])])['createdCount'] === 2, 'HQ role creation failed.');
check($batch->preview($hq, [row(6, ['role' => '公司管理员', 'loginScope' => '仅APP'])])['errorCount'] === 1, 'Manager APP-only scope accepted.');
$export = $admin->users($manager);
check(count($export) === 3, 'Export scope includes other companies.');
foreach ($export as $user) {
    check((int) $user['company_id'] === $companyA && $user['company_code'] === 'BATCH_A', 'Export company mismatch.');
    check(!array_key_exists('password_hash', $user) && !array_key_exists('password', $user), 'Export includes credentials.');
}
echo "User batch: {$checks} permission, validation, atomicity, replay and export assertions passed.\n";

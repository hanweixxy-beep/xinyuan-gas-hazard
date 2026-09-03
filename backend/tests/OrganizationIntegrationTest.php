<?php

use App\Core\Autoloader;
use App\Core\HttpException;
use App\Repositories\CompanyRepository;
use App\Repositories\OrganizationRepository;
use App\Repositories\RecordRepository;
use App\Repositories\UserRepository;
use App\Services\AdminService;

require dirname(__DIR__) . '/app/Core/Autoloader.php';
Autoloader::register(dirname(__DIR__) . '/app');

$database = (string) ($argv[1] ?? '');
if (!preg_match('/^[a-zA-Z0-9_]+$/', $database)) {
    throw new RuntimeException('Pass a temporary database name as the first argument.');
}

$pdo = new PDO(
    "mysql:unix_socket=/var/run/mysqld/mysqld.sock;dbname={$database};charset=utf8mb4",
    'root',
    '',
    [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]
);

$companies = new CompanyRepository($pdo);
$organization = new OrganizationRepository($pdo);
$users = new UserRepository($pdo);
$admin = new AdminService($companies, $organization, $users);
$records = new RecordRepository($pdo);

$company = $pdo->query('SELECT id FROM companies ORDER BY id ASC LIMIT 1')->fetch();
if (!$company) {
    throw new RuntimeException('The temporary database needs one company.');
}
$companyId = (int) $company['id'];
$actor = ['id' => 0, 'role' => 'hq_admin', 'company_id' => $companyId];

$regionId = $admin->createRegion($actor, ['companyId' => $companyId, 'name' => '集成测试片区']);
$storeId = $admin->createStore($actor, [
    'regionId' => $regionId,
    'name' => '集成测试门店',
    'address' => '临时数据库',
]);
$phone = '199' . substr((string) time(), -8);
$userId = $admin->createUser($actor, [
    'companyId' => $companyId,
    'storeId' => $storeId,
    'name' => '组织测试员',
    'phone' => $phone,
    'role' => 'inspector',
    'loginScope' => 'both',
    'password' => 'Temporary-123',
]);
$user = $users->findById($userId);
if (!$user || (int) $user['region_id'] !== $regionId || (int) $user['store_id'] !== $storeId) {
    throw new RuntimeException('Store assignment did not derive its parent region.');
}

$recordId = $records->create($user, [
    'clientUuid' => 'organization-integration-' . time(),
    'customerName' => '组织快照测试用户',
    'customerPhone' => '19900000000',
    'customerType' => '居民用户',
    'customerAddress' => '临时数据库测试地址',
    'inspectorName' => '组织测试员',
    'deliveryPerson' => '测试配送员',
    'inspectionDate' => date('Y-m-d'),
    'checkResult' => '隐患已清零',
    'remark' => '',
    'photos' => [],
]);
$record = $records->findForUser($recordId, $user);
if (!$record || (int) $record['region_id'] !== $regionId || (int) $record['store_id'] !== $storeId) {
    throw new RuntimeException('The inspection record did not retain the organization snapshot.');
}

$rejected = false;
try {
    $admin->createUser($actor, [
        'companyId' => $companyId,
        'name' => '未分配测试员',
        'phone' => '198' . substr((string) time(), -8),
        'role' => 'inspector',
        'loginScope' => 'app',
        'password' => 'Temporary-123',
    ]);
} catch (HttpException $exception) {
    $rejected = $exception->status() === 422;
}
if (!$rejected) {
    throw new RuntimeException('A new non-HQ account without a region or store must be rejected.');
}

echo "Organization integration test passed.\n";

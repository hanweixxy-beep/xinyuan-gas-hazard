<?php

use App\Core\Autoloader;
use App\Core\HttpException;
use App\Repositories\CompanyRepository;
use App\Repositories\OrganizationRepository;
use App\Repositories\RecordRepository;
use App\Repositories\UserRepository;
use App\Services\AdminService;
use App\Services\RecordService;

require dirname(__DIR__) . '/app/Core/Autoloader.php';
Autoloader::register(dirname(__DIR__) . '/app');

$database = (string) ($argv[1] ?? '');
if (!preg_match('/^xinyuan_test_[a-zA-Z0-9_]+$/', $database)) {
    throw new RuntimeException('Pass an isolated database name starting with xinyuan_test_.');
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
$recordService = new RecordService($records, $organization);

$companyId = $companies->create('组织测试公司', 'ORG_TEST');
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
    'name' => '组织测试员',
    'phone' => $phone,
    'role' => 'inspector',
    'loginScope' => 'both',
    'password' => 'Temporary-123',
]);
$user = $users->findById($userId);
if (!$user || $user['region_id'] !== null || $user['store_id'] !== null) {
    throw new RuntimeException('An inspector account must be creatable without a region or store.');
}

$recordId = $recordService->submitDraft($user, [
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
    'photos' => ['storefront' => [['filePath' => 'uploads/organization-test.jpg']]],
    'regionId' => $regionId,
    'storeId' => $storeId,
]);
$record = $records->findForUser($recordId, $user);
if (!$record || (int) $record['region_id'] !== $regionId || (int) $record['store_id'] !== $storeId) {
    throw new RuntimeException('The inspection record did not retain the organization snapshot.');
}

$otherRegionId = $admin->createRegion($actor, ['companyId' => $companyId, 'name' => '跨区检查测试片区']);
$otherStoreId = $admin->createStore($actor, [
    'regionId' => $otherRegionId,
    'name' => '跨区检查测试门店',
    'address' => '临时数据库跨区地址',
]);
$overridePayload = [
    'clientUuid' => 'organization-override-' . time(),
    'customerName' => '跨区检查测试用户',
    'customerPhone' => '19900000001',
    'customerType' => '居民用户',
    'customerAddress' => '临时数据库跨区地址',
    'inspectorName' => '组织测试员',
    'deliveryPerson' => '测试配送员',
    'inspectionDate' => date('Y-m-d'),
    'checkResult' => '隐患已清零',
    'remark' => '',
    'photos' => ['storefront' => [['filePath' => 'uploads/organization-test.jpg']]],
    'regionId' => $otherRegionId,
    'storeId' => $otherStoreId,
];
$overrideRecordId = $recordService->submitDraft($user, $overridePayload);
$overrideRecord = $records->findForUser($overrideRecordId, $user);
if (!$overrideRecord
    || (int) $overrideRecord['region_id'] !== $otherRegionId
    || (int) $overrideRecord['store_id'] !== $otherStoreId) {
    throw new RuntimeException('A cross-region inspection did not retain its selected organization.');
}

$filteredRecords = $recordService->list($user, [
    'regionId' => $otherRegionId,
    'storeId' => $otherStoreId,
]);
$filteredIds = array_map(static fn (array $record): int => (int) $record['id'], $filteredRecords);
if (!in_array($overrideRecordId, $filteredIds, true) || in_array($recordId, $filteredIds, true)) {
    throw new RuntimeException('Region and store filters did not isolate the selected inspection records.');
}

$mismatchRejected = false;
try {
    $recordService->submitDraft($user, array_merge($overridePayload, [
        'clientUuid' => 'organization-mismatch-' . time(),
        'regionId' => $regionId,
    ]));
} catch (HttpException $exception) {
    $mismatchRejected = $exception->status() === 422;
}
if (!$mismatchRejected) {
    throw new RuntimeException('A store outside the selected region must be rejected.');
}

$options = $recordService->organizationOptions($user);
if (count($options['regions']) < 2 || count($options['stores']) < 2) {
    throw new RuntimeException('Organization options did not include all active company regions and stores.');
}

$pdo->prepare("UPDATE regions SET status = 'disabled' WHERE id = :id")->execute(['id' => $otherRegionId]);
$activeOptions = $recordService->organizationOptions($user);
if (array_filter($activeOptions['stores'], static fn (array $store): bool => (int) $store['id'] === $otherStoreId)) {
    throw new RuntimeException('Stores in an inactive region must not appear in inspection options.');
}
$pdo->prepare("UPDATE regions SET status = 'active' WHERE id = :id")->execute(['id' => $otherRegionId]);

// Obsolete account assignments must never supply missing inspection fields.
$pdo->prepare('UPDATE users SET region_id = :region, store_id = :store WHERE id = :id')->execute([
    'region' => $regionId, 'store' => $storeId, 'id' => $userId,
]);
$legacyAssignedUser = $users->findById($userId);
foreach ([[null, null], [$regionId, null], [null, $storeId]] as [$missingRegion, $missingStore]) {
    $rejected = false;
    try {
        $recordService->submitDraft($legacyAssignedUser, array_merge($overridePayload, [
            'clientUuid' => 'organization-incomplete-' . bin2hex(random_bytes(4)),
            'regionId' => $missingRegion,
            'storeId' => $missingStore,
        ]));
    } catch (HttpException $exception) {
        $rejected = $exception->status() === 422;
    }
    if (!$rejected) throw new RuntimeException('Both region and store must be selected for a new inspection.');
}

$legacyPayload = $overridePayload;
unset($legacyPayload['regionId'], $legacyPayload['storeId']);
$legacyPayload['clientUuid'] = 'organization-legacy-' . time();
$companyLevelRecordId = $recordService->create($legacyAssignedUser, $legacyPayload);
$companyLevelRecord = $records->findForUser($companyLevelRecordId, $legacyAssignedUser);
if (!$companyLevelRecord || $companyLevelRecord['region_id'] !== null || $companyLevelRecord['store_id'] !== null) {
    throw new RuntimeException('Legacy imports must preserve absent organization fields without account fallback.');
}

$draftPayload = array_merge($legacyPayload, ['clientUuid' => 'organization-draft-' . time()]);
$draftId = $recordService->saveDraft($user, $draftPayload);
$draftQuery = $pdo->prepare('SELECT record_state, store_id FROM inspection_records WHERE id = :id');
$draftQuery->execute(['id' => $draftId]);
$draft = $draftQuery->fetch();
if (!$draft || $draft['record_state'] !== 'draft' || $draft['store_id'] !== null) {
    throw new RuntimeException('An incomplete inspection may still be saved as a draft.');
}
$finalId = $recordService->submitDraft($user, array_merge($draftPayload, [
    'regionId' => $otherRegionId, 'storeId' => $otherStoreId,
]));
if ($finalId !== $draftId || (int) $records->findForUser($finalId, $user)['store_id'] !== $otherStoreId) {
    throw new RuntimeException('Selecting organization must allow the existing draft to be submitted.');
}

$otherCompanyId = $companies->create('另一测试公司', 'ORG_OTHER');
$foreignRegionId = $admin->createRegion($actor, ['companyId' => $otherCompanyId, 'name' => '其他公司片区']);
$foreignStoreId = $admin->createStore($actor, ['regionId' => $foreignRegionId, 'name' => '其他公司门店']);
$rejected = false;
try {
    $recordService->submitDraft($user, array_merge($overridePayload, [
        'clientUuid' => 'organization-foreign-' . time(),
        'regionId' => $foreignRegionId, 'storeId' => $foreignStoreId,
    ]));
} catch (HttpException $exception) {
    $rejected = $exception->status() === 422;
}
if (!$rejected) throw new RuntimeException('Inspection selection must remain inside the current company.');

foreach (['company_admin', 'hq_admin'] as $index => $role) {
    $createdId = $admin->createUser($actor, [
        'companyId' => $companyId, 'name' => '无绑定管理员',
        'phone' => '198' . $index . substr((string) time(), -7),
        'role' => $role, 'password' => 'Temporary-123',
    ]);
    $created = $users->findById($createdId);
    if ($created['region_id'] !== null || $created['store_id'] !== null) {
        throw new RuntimeException('Manager accounts must not require region or store binding.');
    }
}
$admin->updateUserAccess($actor, $userId, ['loginScope' => 'both', 'canViewCompanyRecords' => true]);
if ((int) $users->findById($userId)['can_view_company_records'] !== 1) {
    throw new RuntimeException('Account access must be editable independently of inspection organization.');
}

echo "Organization integration test passed.\n";

<?php

use App\Core\Autoloader;
use App\Core\Config;
use App\Core\Request;
use App\Http\Controllers\ExportController;
use App\Http\Controllers\RecordController;
use App\Repositories\CompanyRepository;
use App\Repositories\OrganizationRepository;
use App\Repositories\RecordRepository;
use App\Repositories\UserRepository;
use App\Services\AdminService;
use App\Services\AuthService;
use App\Services\ExportService;
use App\Services\RecordService;

require dirname(__DIR__) . '/app/Core/Autoloader.php';
Autoloader::register(dirname(__DIR__) . '/app');
$database = (string) ($argv[1] ?? '');
if (!preg_match('/^xinyuan_test_[a-zA-Z0-9_]+$/', $database)) {
    throw new RuntimeException('An isolated xinyuan_test_ database is required.');
}
$pdo = new PDO("mysql:unix_socket=/var/run/mysqld/mysqld.sock;dbname={$database};charset=utf8mb4", 'root', '', [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES => false,
]);
$companies = new CompanyRepository($pdo);
$organizations = new OrganizationRepository($pdo);
$users = new UserRepository($pdo);
$admin = new AdminService($companies, $organizations, $users);
$records = new RecordRepository($pdo);
$service = new RecordService($records, $organizations);
$companyA = $companies->create('隐患筛选测试甲', 'HAZARD_A');
$companyB = $companies->create('隐患筛选测试乙', 'HAZARD_B');
$hq = ['id' => 0, 'role' => 'hq_admin', 'company_id' => $companyA];
$makeUser = function (int $company, string $phone) use ($admin, $users, $hq): array {
    $id = $admin->createUser($hq, [
        'companyId' => $company, 'name' => '隐患筛选测试员', 'phone' => $phone,
        'role' => 'inspector', 'loginScope' => 'both', 'password' => 'Temporary-Hazard-123',
    ]);
    return $users->findById($id);
};
$userA = $makeUser($companyA, '19900001001');
$userA2 = $makeUser($companyA, '19900001002');
$userB = $makeUser($companyB, '19900001003');
$makeRecord = function (array $user, string $name, array $hazards, string $result = '隐患未清零') use ($records): int {
    return $records->create($user, [
        'customerName' => $name, 'customerPhone' => '19900002000', 'customerType' => '餐饮用户',
        'customerAddress' => '独立临时库测试地址', 'inspectorName' => '测试检查人',
        'deliveryPerson' => '测试配送员', 'inspectionDate' => '2026-10-19',
        'checkResult' => $result, 'hazardItems' => $hazards,
    ]);
};
$alarmA = $makeRecord($userA, 'MATCH_ALARM_A', ['alarm_missing', 'hose_tee']);
$hoseA = $makeRecord($userA, 'EXCLUDED_HOSE_ONLY', ['hose_tee']);
$clearedA = $makeRecord($userA, 'EXCLUDED_CLEARED', [], '隐患已清零');
$rectifiedA = $makeRecord($userA2, 'MATCH_RECTIFIED_A', ['alarm_missing'], '隐患已整改');
$alarmB = $makeRecord($userB, 'EXCLUDED_OTHER_COMPANY', ['alarm_missing']);
$draftA = $makeRecord($userA, 'EXCLUDED_DRAFT', ['alarm_missing']);
$pdo->prepare("UPDATE inspection_records SET record_state = 'draft' WHERE id = ?")->execute([$draftA]);
$assertions = 0;
$check = function (bool $ok, string $message) use (&$assertions): void {
    if (!$ok) throw new RuntimeException($message);
    $assertions++;
};
$expectIds = function (array $actual, array $expected, string $label) use ($check): void {
    $ids = array_map(static fn (array $r): int => (int) $r['id'], $actual);
    sort($ids);
    sort($expected);
    $check($ids === $expected, $label);
};
$companyAdmin = array_merge($userA, ['role' => 'company_admin']);
$companyInspector = array_merge($userA, ['can_view_company_records' => 0]);
$filter = ['hazardCode' => 'alarm_missing'];
$expectIds($service->list($hq, $filter), [$alarmA, $rectifiedA, $alarmB], 'HQ filter / no duplicate rows / no drafts');
$expectIds($service->list($companyAdmin, $filter), [$alarmA, $rectifiedA], 'Company boundary');
$expectIds($service->list($userA, $filter), [$alarmA, $rectifiedA], 'Inspector company records');
$expectIds($service->list($companyInspector, $filter), [$alarmA, $rectifiedA], 'Legacy own-only inspector now has company permission');
$expectIds($service->list($companyInspector, $filter + ['companyId' => $companyB]), [$alarmA, $rectifiedA], 'Inspector cannot override company scope');
$expectIds($service->list($companyAdmin, $filter + ['companyId' => $companyB]), [$alarmA, $rectifiedA], 'Cannot override company scope');
$expectIds($service->list($hq, $filter + ['companyId' => $companyB]), [$alarmB], 'HQ company combination');
$expectIds($service->list($hq, $filter + ['status' => 'rectified']), [$rectifiedA], 'Rectified combination');
$expectIds($service->list($userA, $filter + ['dateFrom' => '2026-10-20']), [], 'Date combination');
$expectIds($service->list($userA, ['hazardCode' => 'hose_tee']), [$alarmA, $hoseA], 'Second hazard on same record');
$expectIds($service->list($userA, ['hazardCode' => 'missing_code']), [], 'Unknown code must not export everything');
$expectIds($service->list($userA, ['hazardCode' => "alarm_missing' OR 1=1 --"]), [], 'Bound parameter injection protection');
$expectIds($service->list($userA, ['hazardCode' => '']), [$alarmA, $hoseA, $clearedA, $rectifiedA], 'Reset / backward compatibility');
$expectIds($service->export($companyAdmin, $filter), [$alarmA, $rectifiedA], 'Export uses same filters and permissions');

$token = bin2hex(random_bytes(32));
$users->createSession((int) $userA['id'], $token, 1, 'admin');
$_SERVER['HTTP_AUTHORIZATION'] = 'Bearer ' . $token;
$_GET = $filter;
$auth = new AuthService($users, new Config([]));
$controller = new RecordController($auth, $service);
$expectIds($controller->index(new Request())['records'], [$alarmA, $rectifiedA], 'List controller passes hazardCode and company scope');
$exportController = new ExportController($auth, $service, new ExportService());
ob_start();
$exportController->xlsx(new Request());
$xlsx = ob_get_clean();
$temp = tempnam(sys_get_temp_dir(), 'hazard-test-');
try {
    file_put_contents($temp, $xlsx);
    $zip = new ZipArchive();
    $check($zip->open($temp) === true, 'Export controller returns readable XLSX');
    $sheet = $zip->getFromName('xl/worksheets/sheet1.xml');
    $zip->close();
    $check(str_contains($sheet, 'MATCH_ALARM_A'), 'Selected record exported');
    $check(!str_contains($sheet, 'EXCLUDED_') && str_contains($sheet, 'MATCH_RECTIFIED_A'), 'Company coworker included, unmatched and other company excluded');
    $check(str_contains($sheet, '有三通'), 'Other hazards on selected record are preserved');
} finally {
    @unlink($temp);
}
$stoveCode = 'stove_no_flame_failure_device';
$regionId = $admin->createRegion($hq, ['companyId' => $companyA, 'name' => '灶具测试片区']);
$storeId = $admin->createStore($hq, ['regionId' => $regionId, 'name' => '灶具测试门店', 'address' => '测试地址']);
$stoveRecordId = $service->submitDraft($userA, [
    'clientUuid' => 'stove-hazard-test', 'regionId' => $regionId, 'storeId' => $storeId,
    'customerName' => '灶具测试用户', 'customerPhone' => '19900002000', 'customerType' => '餐饮用户',
    'customerAddress' => '测试地址', 'inspectorName' => '测试检查人', 'deliveryPerson' => '测试配送员',
    'inspectionDate' => '2026-11-08', 'checkResult' => '隐患未清零',
    'hazardItems' => [$stoveCode],
    'photos' => ['storefront' => ['/uploads/stove-store.jpg'], 'hazard' => [$stoveCode => ['/uploads/stove-hazard.jpg']]],
]);
$stoveRecord = $service->show($stoveRecordId, $userA);
$check($stoveRecord['status'] === 'pending', 'Submitted stove hazard remains pending');
$check($stoveRecord['hazards'][0]['hazard_code'] === $stoveCode
    && $stoveRecord['hazards'][0]['hazard_category'] === '灶具'
    && $stoveRecord['hazards'][0]['hazard_name'] === '灶具无熄火保护装置', 'Stove hazard saves the correct code, category and label');
$check(count(array_filter($stoveRecord['photos'], static fn (array $photo): bool =>
    $photo['photo_type'] === 'hazard' && $photo['hazard_code'] === $stoveCode)) === 1, 'Stove hazard photo linked to the selected item');
$expectIds($service->list($userA, ['hazardCodes' => [$stoveCode]]), [$stoveRecordId], 'Stove hazard filter');
$expectIds($service->export($userA, ['hazardCodes' => [$stoveCode]]), [$stoveRecordId], 'Stove hazard export filter');
echo "Hazard filter: {$assertions} integration assertions passed.\n";

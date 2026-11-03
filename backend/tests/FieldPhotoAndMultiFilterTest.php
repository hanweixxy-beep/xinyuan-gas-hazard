<?php

use App\Core\Autoloader;
use App\Core\HttpException;
use App\Domain\HazardCatalog;
use App\Repositories\CompanyRepository;
use App\Repositories\OrganizationRepository;
use App\Repositories\RecordRepository;
use App\Repositories\UserRepository;
use App\Services\AdminService;
use App\Services\RecordService;
use App\Services\ExportJobService;

require dirname(__DIR__) . '/app/Core/Autoloader.php';
Autoloader::register(dirname(__DIR__) . '/app');
$db = (string) ($argv[1] ?? '');
if (!preg_match('/^xinyuan_test_[a-zA-Z0-9_]+$/', $db)) throw new RuntimeException('Isolated test database required.');
$pdo = new PDO("mysql:unix_socket=/var/run/mysqld/mysqld.sock;dbname={$db};charset=utf8mb4", 'root', '', [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES => false,
]);
$companies = new CompanyRepository($pdo);
$org = new OrganizationRepository($pdo);
$users = new UserRepository($pdo);
$admin = new AdminService($companies, $org, $users);
$records = new RecordRepository($pdo);
$service = new RecordService($records, $org);
$company = $companies->create('拍照多选测试', 'FIELD_MULTI');
$hq = ['id' => 0, 'role' => 'hq_admin', 'company_id' => $company];
$makeUser = function (string $phone) use ($admin, $users, $hq, $company): array {
    return $users->findById($admin->createUser($hq, ['companyId' => $company, 'name' => '测试', 'phone' => $phone,
        'role' => 'inspector', 'loginScope' => 'both', 'password' => 'Temporary-Field-123']));
};
$user = $makeUser('19900004001');
$other = $makeUser('19900004002');
$region = $admin->createRegion($hq, ['companyId' => $company, 'name' => '测试片区']);
$store = $admin->createStore($hq, ['regionId' => $region, 'name' => '测试门店', 'address' => '临时库']);
$assertions = 0;
$check = function (bool $ok, string $message) use (&$assertions): void { if (!$ok) throw new RuntimeException($message); $assertions++; };
$reject = function (callable $operation, string $message) use ($check): void {
    try { $operation(); } catch (HttpException $error) { $check($error->status() === 422, $message); return; }
    throw new RuntimeException($message);
};
$check(in_array('虚假安检', HazardCatalog::gasSourceIssues(), true), 'New gas-source issue in server catalog');
$partial = ['clientUuid' => 'partial-field-test', 'deliveryPerson' => '配送测试', 'photos' => ['storefront' => ['/uploads/fake.jpg']]];
$draftId = $service->saveDraft($user, $partial);
$draft = $pdo->query('SELECT * FROM inspection_records WHERE id = ' . $draftId)->fetch();
$check($draft['record_state'] === 'draft' && $draft['customer_name'] === '' && $draft['customer_type'] === '', 'Incomplete draft saved without fabricated user data');
$check($service->list($user) === [], 'Draft invisible as formal record');
$valid = ['clientUuid' => 'complete-field-test', 'customerName' => 'MULTI_ALARM_FAKE', 'customerPhone' => '0019900000000',
    'customerType' => '餐饮用户', 'customerAddress' => '测试地址', 'inspectorName' => '测试检查', 'deliveryPerson' => '测试配送',
    'inspectionDate' => '2026-10-20', 'checkResult' => '隐患未清零', 'regionId' => $region, 'storeId' => $store,
    'hazardItems' => ['alarm_missing'], 'gasSourceIssues' => ['虚假安检'],
    'photos' => ['storefront' => ['/uploads/store.jpg'], 'hazard' => ['alarm_missing' => ['/uploads/alarm.jpg']], 'cylinder' => ['/uploads/cylinder.jpg']]];
foreach (['customerName', 'customerPhone', 'customerType', 'customerAddress', 'inspectorName', 'deliveryPerson', 'inspectionDate', 'checkResult'] as $field) {
    $bad = $valid;
    $bad[$field] = '';
    $reject(fn () => $service->submitDraft($user, $bad), 'Submission must require ' . $field);
}
$noGasPhoto = $valid;
$noGasPhoto['photos']['cylinder'] = [];
$reject(fn () => $service->submitDraft($user, $noGasPhoto), 'Gas-source issue requires a supporting photo');
$id1 = $service->submitDraft($user, $valid);
$repaired = $valid;
$repaired['clientUuid'] = $partial['clientUuid'];
$repaired['customerName'] = 'COMPLETED_PARTIAL';
$completedId = $service->submitDraft($user, $repaired);
$check($completedId === $draftId && $service->show($completedId, $user)['customer_name'] === 'COMPLETED_PARTIAL', 'Incomplete draft can be completed and submitted');
$make = function (array $who, string $name, array $hazards, array $gas) use ($records): int {
    return $records->create($who, ['customerName' => $name, 'customerPhone' => '19900000000', 'customerType' => '餐饮用户',
        'customerAddress' => '临时测试', 'inspectorName' => '测试', 'deliveryPerson' => '测试', 'inspectionDate' => '2026-10-20',
        'checkResult' => '隐患未清零', 'hazardItems' => $hazards, 'gasSourceIssues' => $gas]);
};
$id2 = $make($user, 'MULTI_HOSE_QR', ['hose_tee'], ['钢瓶无二维码']);
$id3 = $make($user, 'MULTI_ALARM_OTHER_GAS', ['alarm_missing', 'hose_tee'], ['钢瓶超期或报废']);
$id4 = $make($user, 'MULTI_GAS_ONLY', [], ['虚假安检', '钢瓶无二维码']);
$idOther = $make($other, 'OTHER_OWNER', ['alarm_missing'], ['虚假安检']);
$expect = function (array $filters, array $expected) use ($service, $user, $check): void {
    $actual = array_map(static fn ($r) => (int) $r['id'], $service->list($user, $filters));
    sort($actual); sort($expected);
    $check($actual === $expected, 'Multi-filter result set: ' . json_encode($filters, JSON_UNESCAPED_UNICODE));
};
$expect(['hazardCodes' => ['alarm_missing', 'hose_tee']], [$id1, $completedId, $id2, $id3]);
$expect(['gasSourceIssues' => ['虚假安检', '钢瓶无二维码']], [$id1, $completedId, $id2, $id4]);
$both = ['hazardCodes' => ['alarm_missing', 'hose_tee'], 'gasSourceIssues' => ['虚假安检', '钢瓶无二维码']];
$expect($both, [$id1, $completedId, $id2]);
$expect($both + ['keyword' => 'MULTI_'], [$id1, $id2]);
$expect(['hazardCodes' => ['alarm_missing', 'alarm_missing']], [$id1, $completedId, $id3]);
$expect(['hazardCode' => 'hose_tee'], [$id2, $id3]);
$expect(['hazardCodes' => ["x' OR 1=1 --"]], []);
$expect(['gasSourceIssues' => ["x' OR 1=1 --"]], []);
$expect(['hazardCodes' => [], 'gasSourceIssues' => []], [$id1, $completedId, $id2, $id3, $id4]);
$reject(fn () => $service->list($user, ['gasSourceIssues' => [['nested']]]), 'Nested filters rejected');
$reject(fn () => $service->list($user, ['hazardCodes' => array_fill(0, 31, 'alarm_missing')]), 'Excess filter values rejected');
$check(count($service->list($hq, $both)) >= 4, 'HQ scope remains usable with multi-filters');
$check(count($service->export($user, $both)) === 3, 'Synchronous export preserves multi-filters');
$root = sys_get_temp_dir() . '/xinyuan-field-jobs-' . bin2hex(random_bytes(5));
$jobs = new ExportJobService($pdo, $records, $root, $root . '/uploads');
try {
    $job = $jobs->create($user, $both + ['keyword' => 'MULTI_']);
    $check($job['total'] === 2 && $job['filters']['gasSourceIssues'] === $both['gasSourceIssues'], 'Background export snapshots selected arrays');
    $jobs->workOnce();
    $result = $jobs->list($user)[0];
    $check($result['status'] === 'finished' && $result['processed'] === 2, 'Background multi-filter export finishes');
    $zip = new ZipArchive();
    $check($zip->open($root . '/job-' . $job['id'] . '/' . $result['files'][0]['name']) === true, 'Actual multi-filter XLSX readable');
    $sheet = $zip->getFromName('xl/worksheets/sheet1.xml');
    $zip->close();
    $check(str_contains($sheet, 'MULTI_ALARM_FAKE') && str_contains($sheet, 'MULTI_HOSE_QR') && str_contains($sheet, '虚假安检'), 'Both selected groups included');
    $check(!str_contains($sheet, 'MULTI_GAS_ONLY') && !str_contains($sheet, 'OTHER_OWNER') && !str_contains($sheet, 'MULTI_ALARM_OTHER_GAS'), 'Nonmatching and unauthorized records excluded from actual file');
} finally {
    $iterator = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS), RecursiveIteratorIterator::CHILD_FIRST);
    foreach ($iterator as $file) { $file->isDir() ? rmdir($file->getPathname()) : unlink($file->getPathname()); }
    rmdir($root);
}
echo "Field draft, submission and multi-filter: {$assertions} assertions passed.\n";

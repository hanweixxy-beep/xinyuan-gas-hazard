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

$ids = [];
for ($i = 0; $i < 1205; $i++) {
    $ids[] = $records->create($user, ['customerName' => 'PAGE_' . $i, 'customerPhone' => '19900000000',
        'customerType' => '餐饮用户', 'customerAddress' => '临时库', 'inspectorName' => '测试', 'deliveryPerson' => '测试',
        'inspectionDate' => $i < 1200 ? '2026-10-25' : '2026-10-01',
        'checkResult' => $i < 1200 ? '隐患已清零' : '隐患未清零',
        'hazardItems' => $i < 1200 ? [] : ['alarm_missing'],
        'gasSourceIssues' => $i < 1200 ? [] : ['虚假安检']]);
}
$service->saveDraft($user, ['clientUuid' => 'pagination-draft', 'deliveryPerson' => '测试']);
$page = $service->page($user, [], 1, 50);
$check($page['pagination']['total'] === 1205, 'Full total exceeds legacy cap, excludes draft');
$check($page['summary']['pending'] === 5 && $page['summary']['done'] === 1200, 'Full status counts');
$check(count($page['summary']['pendingRecords']) === 5, 'Pending preview includes records outside first 1000');
$check(array_sum(array_column($page['summary']['trend'], 'count')) === 1205, 'Trend full totals');
$check(count($page['records']) === 50 && $page['pagination']['pageCount'] === 25, 'Bounded page and page count');
$all = [];
for ($number = 1; $number <= 25; $number++) {
    $result = $service->page($user, [], $number, 50);
    $all = array_merge($all, array_column($result['records'], 'id'));
}
$check(count($all) === 1205 && count(array_unique($all)) === 1205, 'Every record reachable without duplicates');
$check($all === array_merge(array_reverse(array_slice($ids, 0, 1200)), array_reverse(array_slice($ids, 1200))), 'Stable date and ID order');
$last = $service->page($user, [], 9999, 50);
$check($last['pagination']['page'] === 25 && count($last['records']) === 5, 'Oversized page clamps to last page');
$shared = $service->page($other, [], 1, 50);
$check($shared['pagination']['total'] === 1205 && $shared['summary']['pending'] === 5, 'Coworker sees company rows and matching aggregates');
$otherCompany = $companies->create('分页隔离公司', 'PAGE_OTHER');
$outsider = array_merge($other, ['company_id' => $otherCompany]);
$empty = $service->page($outsider, ['companyId' => $company], 1, 50);
$check($empty['pagination']['total'] === 0 && $empty['summary']['pendingRecords'] === [], 'Company boundary preserved in aggregates and rows');
$filtered = $service->page($user, ['hazardCodes' => ['alarm_missing', 'hose_tee'], 'gasSourceIssues' => ['虚假安检']], 1, 50);
$check($filtered['pagination']['total'] === 5 && $filtered['summary']['pending'] === 5, 'Multi filters shared by page and aggregates');
$done = $service->page($user, ['status' => 'done'], 1, 50);
$check($done['pagination']['total'] === 1200 && $done['summary']['pendingRecords'] === [], 'Status filter also restricts dashboard preview');
$check(count($service->list($user)) === 1000, 'Legacy client behavior compatible');
echo "Pagination integration passed: {$assertions} assertions, 1205 records.\n";

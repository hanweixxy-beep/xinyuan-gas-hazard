<?php
declare(strict_types=1);

use App\Core\Autoloader;
use App\Core\Config;
use App\Core\HttpException;
use App\Core\Request;
use App\Http\Controllers\RecordController;
use App\Repositories\CompanyRepository;
use App\Repositories\OrganizationRepository;
use App\Repositories\RecordRepository;
use App\Repositories\UserRepository;
use App\Services\AdminService;
use App\Services\AuthService;
use App\Services\RecordService;

require dirname(__DIR__) . '/app/Core/Autoloader.php';
Autoloader::register(dirname(__DIR__) . '/app');
$database = (string) ($argv[1] ?? '');
if (!preg_match('/^xinyuan_test_[a-zA-Z0-9_]+$/', $database)) {
    throw new RuntimeException('An isolated test database is required.');
}
$pdo = new PDO("mysql:unix_socket=/var/run/mysqld/mysqld.sock;dbname={$database};charset=utf8mb4", 'root', '', [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES => false,
]);
$companies = new CompanyRepository($pdo);
$users = new UserRepository($pdo);
$organization = new OrganizationRepository($pdo);
$admin = new AdminService($companies, $organization, $users);
$records = new RecordRepository($pdo);
$service = new RecordService($records, $organization);
$company = $companies->create('协同整改测试公司', 'RECTIFY_QA');
$hq = ['id' => 0, 'role' => 'hq_admin', 'company_id' => $company];
$makeUser = function (string $name, string $phone, string $role, bool $companyView, ?int $companyId = null) use ($admin, $users, $hq, $company): array {
    $id = $admin->createUser($hq, ['companyId' => $companyId ?? $company, 'name' => $name, 'phone' => $phone,
        'password' => 'Isolated-Rectification-123', 'role' => $role, 'loginScope' => 'both',
        'canViewCompanyRecords' => $companyView]);
    return $users->findById($id);
};
$creator = $makeUser('原检查人', '19900006001', 'inspector', false);
$coworker = $makeUser('协同整改人', '19900006002', 'inspector', true);
$manager = $makeUser('公司管理员', '19900006003', 'company_admin', false);
$makeRecord = function (string $result = '隐患未清零', ?array $owner = null) use ($service, &$creator): int {
    return $service->create($owner ?? $creator, ['customerName' => '隔离整改样本', 'customerPhone' => '19900000000',
        'customerType' => '居民用户', 'customerAddress' => '临时测试地址', 'inspectorName' => '原检查人',
        'deliveryPerson' => '测试配送员', 'inspectionDate' => '2026-11-06', 'checkResult' => $result,
        'hazardItems' => $result === '隐患未清零' ? ['alarm_missing'] : [], 'photos' => []]);
};
$payload = ['rectificationDate' => '2026-11-06', 'rectificationNote' => '测试完成整改',
    'photos' => ['/uploads/qa-rectification.jpg']];
$checks = 0;
$check = function (bool $ok, string $message) use (&$checks): void {
    if (!$ok) throw new RuntimeException($message);
    $checks++;
};
$expectHttp = function (callable $operation, int $status) use ($check): void {
    try { $operation(); } catch (HttpException $error) {
        $check($error->status() === $status, 'Unexpected HTTP status');
        return;
    }
    throw new RuntimeException('Expected an HTTP rejection.');
};

$check((int)$creator['can_view_company_records'] === 1, 'New inspector must receive company collaboration even when legacy flag is false');
$admin->updateUserAccess($hq, (int)$creator['id'], ['loginScope' => 'both', 'canViewCompanyRecords' => false]);
$check((int)$users->findById((int)$creator['id'])['can_view_company_records'] === 1, 'Permission edit must retain company collaboration');
$pdo->prepare('UPDATE users SET can_view_company_records = 0 WHERE id = ?')->execute([$creator['id']]);
$creator = $users->findById((int)$creator['id']);
$auth = new AuthService($users, new Config([]));
$check($auth->publicUser($creator)['canViewCompanyRecords'] === true, 'Existing APP must receive effective company scope for legacy accounts');
$sharedId = $makeRecord('隐患未清零', $coworker);
$check(in_array($sharedId, array_column($service->list($creator), 'id')), 'Legacy own-only account must find a coworker record in the normal list');
$sharedPage = $service->page($creator, ['status' => 'pending'], 1, 50);
$check($sharedPage['pagination']['total'] === 1 && $sharedPage['summary']['pending'] === 1, 'Company pagination and pending counts must match');
$check((int)$service->show($sharedId, $creator)['creator_id'] === (int)$coworker['id'], 'Legacy account can open coworker details');
$token = bin2hex(random_bytes(32));
$users->createSession((int)$creator['id'], $token, 1, 'app');
$_SERVER['HTTP_AUTHORIZATION'] = 'Bearer ' . $token;
$controller = new RecordController($auth, $service);
$check(count($controller->index(new Request())['records']) === 1, 'Authenticated list controller must expose company records');
$check((int)$controller->show(new Request(), ['id' => (string)$sharedId])['record']['id'] === $sharedId, 'Authenticated details controller must allow coworker records');
unset($_SERVER['HTTP_AUTHORIZATION']);
$expectHttp(fn () => $controller->index(new Request()), 401);
$expectHttp(fn () => $controller->rectify(new Request(), ['id' => (string)$sharedId]), 401);
$service->rectify($sharedId, $creator, $payload);
$check($service->show($sharedId, $coworker)['rectification_person'] === $creator['name'], 'Legacy own-only inspector must rectify coworker records');

$otherCompany = $companies->create('整改隔离公司', 'RECTIFY_OTHER');
$outsider = $makeUser('其他公司检查员', '19900006004', 'inspector', false, $otherCompany);
$foreign = $makeRecord('隐患未清零', $outsider);
foreach ([$creator, $coworker, $manager] as $actor) {
    $check(!in_array($foreign, array_column($service->list($actor, ['companyId' => $otherCompany]), 'id')), 'Cannot widen company scope with a filter');
    $expectHttp(fn () => $service->show($foreign, $actor), 404);
    $expectHttp(fn () => $service->rectify($foreign, $actor, $payload + ['companyId' => $otherCompany]), 404);
}
$check($service->show($foreign, $outsider)['status'] === 'pending', 'Cross-company attempts must leave the record unchanged');
$service->rectify($foreign, $hq, $payload);
$check($service->show($foreign, $outsider)['status'] === 'rectified', 'Existing HQ cross-company access must remain available');

$id = $makeRecord();
$service->rectify($id, $coworker, $payload);
$fixed = $records->findForUser($id, $coworker);
$check($fixed['status'] === 'rectified', 'A coworker must be able to rectify a visible record');
$check($fixed['rectification_person'] === $coworker['name'], 'Default rectifier must be the acting user');
$check((int)$fixed['creator_id'] === (int)$creator['id'] && $fixed['inspector_name'] === '原检查人', 'Original inspection ownership must be preserved');
$check(count($fixed['photos']) === 1 && $fixed['photos'][0]['photo_type'] === 'rectification', 'Rectification evidence must be stored');
$expectHttp(fn () => $service->rectify($id, $creator, $payload + ['rectificationPerson' => '重复提交者']), 409);
$check($records->findForUser($id, $creator)['rectification_person'] === $coworker['name'], 'A repeat must not overwrite the completed rectification');
$check(count($records->findForUser($id, $creator)['photos']) === 1, 'A repeat must not append duplicate photos');

$own = $makeRecord();
$service->rectify($own, $creator, $payload);
$check($records->findForUser($own, $creator)['status'] === 'rectified', 'Creator rectification must still work');
$managed = $makeRecord();
$service->rectify($managed, $manager, $payload);
$check($records->findForUser($managed, $manager)['rectification_person'] === $manager['name'], 'Company admin rectification must still work');
$expectHttp(fn () => $service->rectify(999999999, $coworker, $payload), 404);
$expectHttp(fn () => $service->rectify($makeRecord('隐患已清零'), $coworker, $payload), 409);
$expectHttp(fn () => $service->rectify($makeRecord(), $coworker, []), 422);
$draft = $service->saveDraft($creator, ['clientUuid' => 'rectification-qa-draft', 'deliveryPerson' => '测试配送员']);
$expectHttp(fn () => $service->rectify($draft, $coworker, $payload), 404);

$rollback = $makeRecord();
$pdo->exec("CREATE TRIGGER qa_rectification_photo_error BEFORE INSERT ON record_photos FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected isolated photo failure'");
try {
    try {
        $service->rectify($rollback, $coworker, $payload);
        throw new RuntimeException('Expected the simulated photo failure');
    } catch (PDOException $error) {
        $check($error->getCode() === '45000', 'Expected isolated trigger failure');
    }
} finally {
    $pdo->exec('DROP TRIGGER qa_rectification_photo_error');
}
$unchanged = $records->findForUser($rollback, $creator);
$check($unchanged['status'] === 'pending' && empty($unchanged['rectification_person']), 'Photo failure must roll back the record update');
$check($unchanged['photos'] === [], 'Photo failure must not leave partial evidence');
$service->rectify($rollback, $coworker, $payload);
$check($records->findForUser($rollback, $coworker)['status'] === 'rectified', 'Retry must work after a rolled-back failure');
echo "Rectification access regression passed: {$checks} assertions.\n";

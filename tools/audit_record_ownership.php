<?php
// Read-only ownership/count audit. No credentials, customer details or photos are output.
use App\Core\Autoloader;
use App\Domain\UserAccess;
use App\Repositories\RecordRepository;
$project = (string) ($argv[1] ?? '/var/www/xinyuan-gas');
require $project . '/backend/app/Core/Autoloader.php';
Autoloader::register($project . '/backend/app');
$pdo = new PDO('mysql:unix_socket=/var/run/mysqld/mysqld.sock;dbname=xinyuan_gas;charset=utf8mb4', 'root', '', [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES => false,
]);
$pdo->exec('SET SESSION TRANSACTION READ ONLY');
$users = $pdo->query('SELECT id, name, role, company_id, can_view_company_records, login_scope, status FROM users ORDER BY id')->fetchAll();
$records = $pdo->query("SELECT id, creator_id, company_id, inspector_name, delivery_person, status, inspection_date, created_at
    FROM inspection_records WHERE record_state = 'submitted'")->fetchAll();
$userMap = array_column($users, null, 'id');
$repo = new RecordRepository($pdo);
$results = []; $target = [];
foreach ($users as $user) {
    $own = array_values(array_filter($records, static fn ($r) => (int)$r['creator_id'] === (int)$user['id']));
    $company = array_values(array_filter($records, static fn ($r) => (int)$r['company_id'] === (int)$user['company_id']));
    $inScope = $user['role'] === 'hq_admin' ? $records : $company;
    $exact = array_values(array_filter($inScope, static fn ($r) => trim($r['inspector_name']) === trim($user['name'])));
    $contains = array_values(array_filter($inScope, static fn ($r) => str_contains($r['inspector_name'], trim($user['name']))));
    if (!$own && !$exact && !$contains && $user['name'] !== '陈芳') continue;
    $scope = UserAccess::recordScope($user);
    $visible = $scope === 'all' ? $records : ($scope === 'company' ? $company : $own);
    $others = array_values(array_filter($contains, static fn ($r) => (int)$r['creator_id'] !== (int)$user['id']));
    $ownDifferentName = array_filter($own, static fn ($r) => !str_contains($r['inspector_name'], trim($user['name'])));
    $entry = ['userId' => (int)$user['id'], 'name' => $user['name'], 'status' => $user['status'], 'scope' => $scope,
        'selfRecorded' => count($own), 'serverPermissionVisible' => count($visible),
        'adminInspectorExact' => count($exact), 'adminInspectorContains' => count($contains),
        'inspectorMatchesRecordedByOthers' => count($others), 'selfRecordedWithDifferentInspector' => count($ownDifferentName)];
    if ($user['status'] === 'active') {
        $serverIds = iterator_to_array($repo->exportIds($user, []), false);
        $expectedIds = array_map('intval', array_column($visible, 'id')); sort($serverIds); sort($expectedIds);
        $entry['serverScopeMatchesPermissionQuery'] = $serverIds === $expectedIds;
    }
    $results[] = $entry;
    if ($user['name'] === '陈芳') {
        $groups = []; $labels = [];
        foreach ($contains as $r) {
            $labels[$r['inspector_name']] = ($labels[$r['inspector_name']] ?? 0) + 1;
            $id = (int)$r['creator_id'];
            $groups[$id] ??= ['creatorId' => $id, 'creatorName' => $userMap[$id]['name'] ?? '(removed)', 'records' => 0,
                'inspectionDateMin' => $r['inspection_date'], 'inspectionDateMax' => $r['inspection_date']];
            $groups[$id]['records']++;
            $groups[$id]['inspectionDateMin'] = min($groups[$id]['inspectionDateMin'], $r['inspection_date']);
            $groups[$id]['inspectionDateMax'] = max($groups[$id]['inspectionDateMax'], $r['inspection_date']);
        }
        $pages = []; $pageIds = [];
        for ($page = 1; ; $page++) {
            $data = $repo->pageForUser($user, [], $page, 100);
            $pages[] = ['page' => $page, 'count' => count($data['records']), 'pagination' => $data['pagination']];
            array_push($pageIds, ...array_map('intval', array_column($data['records'], 'id')));
            if ($page >= $data['pagination']['pageCount']) break;
        }
        $target[] = $entry + ['creatorGroupsForNamedInspections' => array_values($groups), 'inspectorLabels' => $labels, 'permissionPages' => $pages,
            'permissionUniqueIds' => count(array_unique($pageIds)),
            'permissionPending' => count(array_filter($visible, static fn ($r) => $r['status'] === 'pending'))];
    }
}
echo json_encode(['auditedAt' => date(DATE_ATOM), 'totalSubmittedRecords' => count($records), 'target' => $target,
    'activeAccountsWithNameCountDifference' => count(array_filter($results, static fn ($r) => $r['status'] === 'active' && $r['selfRecorded'] !== $r['adminInspectorContains'])),
    'serverScopeMismatches' => array_values(array_filter($results, static fn ($r) => isset($r['serverScopeMatchesPermissionQuery']) && !$r['serverScopeMatchesPermissionQuery'])),
    'accounts' => $results], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR) . PHP_EOL;

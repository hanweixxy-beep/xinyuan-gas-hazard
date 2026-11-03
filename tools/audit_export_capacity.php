<?php
// Read-only production audit. Never prints credentials or customer data.
use App\Core\Autoloader;
use App\Domain\HazardCatalog;
use App\Repositories\RecordRepository;

$project = (string) ($argv[1] ?? '/var/www/xinyuan-gas');
$jobId = (int) ($argv[2] ?? 56);
require $project . '/backend/app/Core/Autoloader.php';
Autoloader::register($project . '/backend/app');
$pdo = new PDO('mysql:unix_socket=/var/run/mysqld/mysqld.sock;dbname=xinyuan_gas;charset=utf8mb4', 'root', '', [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES => false,
]);
$pdo->exec('SET SESSION TRANSACTION READ ONLY');
$stmt = $pdo->prepare('SELECT id, requested_by, status, filters_json FROM export_jobs WHERE id = ?');
$stmt->execute([$jobId]);
$job = $stmt->fetch();
if (!$job) throw new RuntimeException('Requested export job not found');
$meta = json_decode($job['filters_json'], true, 512, JSON_THROW_ON_ERROR);
$stmt = $pdo->prepare('SELECT id, role, company_id, can_view_company_records, login_scope, status FROM users WHERE id = ?');
$stmt->execute([$job['requested_by']]);
$user = $stmt->fetch();
if (!$user) throw new RuntimeException('Export owner not found');
$repo = new RecordRepository($pdo);
$allIds = iterator_to_array($repo->exportIds($user, []), false);
$actualIds = iterator_to_array($repo->exportIds($user, $meta['filters'] ?? []), false);
$allGasIds = iterator_to_array($repo->exportIds($user, ['gasSourceIssues' => HazardCatalog::gasSourceIssues()]), false);
$counts = [];
foreach (HazardCatalog::gasSourceIssues() as $issue) {
    $counts[$issue] = count(iterator_to_array($repo->exportIds($user, ['gasSourceIssues' => [$issue]]), false));
}
$scopeIds = implode(',', array_map('intval', $allIds)) ?: '0';
$stored = $pdo->query("SELECT issue_text, COUNT(DISTINCT record_id) AS records FROM record_gas_source_issues
    WHERE record_id IN ({$scopeIds}) GROUP BY issue_text ORDER BY issue_text")->fetchAll();
$anyGas = (int) $pdo->query("SELECT COUNT(DISTINCT record_id) FROM record_gas_source_issues WHERE record_id IN ({$scopeIds})")->fetchColumn();
$pageIds = [];
$pageSummary = [];
for ($page = 1; ; $page++) {
    $data = $repo->pageForUser($user, $meta['filters'] ?? [], $page, 50);
    foreach ($data['records'] as $record) $pageIds[] = (int) $record['id'];
    $pageSummary[] = ['requested' => $page, 'returned' => count($data['records']), 'pagination' => $data['pagination']];
    if ($page >= $data['pagination']['pageCount']) break;
}
$jobRoot = '/var/lib/xinyuan-gas-exports/job-' . $jobId;
$snapshot = array_map('intval', file($jobRoot . '/ids.txt', FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES));
$progress = json_decode(file_get_contents($jobRoot . '/progress.json'), true, 512, JSON_THROW_ON_ERROR);
$unknown = array_values(array_filter($stored, static fn ($row) => !in_array($row['issue_text'], HazardCatalog::gasSourceIssues(), true)));
$sort = static function (array $ids): array { sort($ids); return $ids; };
$nonEmptyFilters = array_filter($meta['filters'] ?? [], static fn ($v) => is_array($v) ? count($v) > 0 : $v !== '');
$active = (int) $pdo->query("SELECT COUNT(*) FROM export_jobs WHERE status IN ('queued','processing')")->fetchColumn();
echo json_encode([
    'auditedAt' => date(DATE_ATOM), 'job' => $jobId, 'jobStatus' => $job['status'], 'ownerRole' => $user['role'],
    'filters' => $nonEmptyFilters, 'jobSnapshotTotal' => count($snapshot), 'jobMetadataTotal' => $meta['total'] ?? null,
    'progress' => ['processed' => $progress['processed'] ?? null, 'photos' => $progress['photos'] ?? null, 'files' => $progress['files'] ?? []],
    'currentExactFilterTotal' => count($actualIds), 'allSixGasIssuesTotal' => count($allGasIds), 'allVisibleRecords' => count($allIds),
    'recordsWithAnyStoredGasIssue' => $anyGas, 'recordsWithoutGasIssue' => count($allIds) - $anyGas,
    'perIssueDistinctRecords' => $counts, 'unrecognizedStoredIssueValues' => $unknown,
    'pages' => $pageSummary, 'paginationUniqueRecords' => count(array_unique($pageIds)),
    'paginationMatchesFullQuery' => $sort($pageIds) === $sort($actualIds),
    'snapshotMatchesCurrentQuery' => $sort($snapshot) === $sort($actualIds),
    'activeExportJobs' => $active, 'diskFreeBytes' => disk_free_space('/var/lib/xinyuan-gas-exports'),
    'systemMemory' => array_values(preg_grep('/^(MemTotal|MemAvailable|SwapTotal|SwapFree):/', file('/proc/meminfo'))),
], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR) . PHP_EOL;

<?php
// Isolated benchmark: reads production job scope, writes only under a private /var/tmp test root.
use App\Core\Autoloader;
use App\Repositories\RecordRepository;
use App\Services\ExportImageService;
use App\Services\ExportService;

$project = (string) ($argv[1] ?? '');
$root = (string) ($argv[2] ?? '');
$mode = (string) ($argv[3] ?? '');
$multiplier = (int) ($argv[4] ?? 1);
if (!preg_match('#^/var/tmp/xinyuan-export-capacity-[a-zA-Z0-9_-]+$#', $root)) throw new RuntimeException('Private benchmark root required');
if (!in_array($mode, ['prepare', 'workbook'], true) || $multiplier < 1 || $multiplier > 8) throw new RuntimeException('Invalid benchmark mode');
require $project . '/backend/app/Core/Autoloader.php';
Autoloader::register($project . '/backend/app');
$pdo = new PDO('mysql:unix_socket=/var/run/mysqld/mysqld.sock;dbname=xinyuan_gas;charset=utf8mb4', 'root', '', [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
]);
$pdo->exec('SET SESSION TRANSACTION READ ONLY');
if ((int) $pdo->query("SELECT COUNT(*) FROM export_jobs WHERE status IN ('queued','processing')")->fetchColumn() > 0) {
    fwrite(STDERR, "BENCHMARK_DEFERRED: live exports running\n"); exit(75);
}
if (disk_free_space('/var/tmp') < 6 * 1024 ** 3) throw new RuntimeException('Less than 6GiB free disk; test stopped');
$memoryInfo = file_get_contents('/proc/meminfo');
preg_match('/MemAvailable:\s+(\d+)/', $memoryInfo, $match);
if ((int) ($match[1] ?? 0) < 1536 * 1024) throw new RuntimeException('Less than 1.5GiB available system memory; test stopped');
umask(0077);
if (!is_dir($root)) mkdir($root, 0700);
$start = hrtime(true);
$uploads = $project . '/backend/storage/uploads';
$images = new ExportImageService($uploads, $root . '/images');
if ($mode === 'prepare') {
    $job = $pdo->query('SELECT requested_by, filters_json FROM export_jobs WHERE id = 56')->fetch();
    if (!$job) throw new RuntimeException('Job 56 not found');
    $user = $pdo->query('SELECT id, role, company_id, can_view_company_records, login_scope, status FROM users WHERE id = ' . (int) $job['requested_by'])->fetch();
    $ids = array_map('intval', file('/var/lib/xinyuan-gas-exports/job-56/ids.txt', FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES));
    $records = [];
    $repo = new RecordRepository($pdo);
    foreach (array_chunk($ids, 50) as $batch) array_push($records, ...$repo->exportBatch($user, $batch));
    if (count($records) !== count($ids)) throw new RuntimeException('Export scope changed');
    file_put_contents($root . '/fixture.json', json_encode($records, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR));
    $photos = 0; $bytes = 0; $missing = 0;
    foreach ($records as $record) foreach ($record['photos'] as $photo) {
        $copy = $images->prepare($photo['file_path']);
        $photos++;
        if (!$copy) $missing++; else $bytes += filesize($copy['path']);
        if ($photos % 100 === 0) echo json_encode(['phase' => 'prepare', 'photos' => $photos, 'seconds' => (hrtime(true) - $start) / 1e9]) . PHP_EOL;
    }
    $result = ['phase' => 'prepare', 'records' => count($records), 'photos' => $photos, 'missingPhotos' => $missing, 'compressedPhotoBytes' => $bytes];
    if ($missing) throw new RuntimeException('Missing photos; capacity result would be incomplete');
} else {
    $fixture = json_decode(file_get_contents($root . '/fixture.json'), true, 512, JSON_THROW_ON_ERROR);
    $records = [];
    for ($repeat = 0; $repeat < $multiplier; $repeat++) foreach ($fixture as $record) {
        if ($multiplier > 1) {
            $record['customer_name'] = '容量模拟_重复样本_' . count($records);
            $record['customer_phone'] = 'CAPACITY_TEST';
            $record['customer_address'] = '重复真实照片用于容量测试，不是真实新增记录';
        }
        $records[] = $record;
    }
    $target = $root . '/single-' . count($records) . '.xlsx';
    if (file_exists($target)) throw new RuntimeException('Benchmark output already exists');
    $exporter = new ExportService($uploads, $images, $root);
    $temp = $exporter->recordsWorkbookFile($records);
    if (!rename($temp, $target)) throw new RuntimeException('Could not save test output');
    $writeSeconds = (hrtime(true) - $start) / 1e9;
    $zip = new ZipArchive();
    if ($zip->open($target, ZipArchive::CHECKCONS) !== true) throw new RuntimeException('Workbook ZIP integrity check failed');
    $embedded = 0;
    for ($i = 0; $i < $zip->numFiles; $i++) if (str_starts_with($zip->getNameIndex($i), 'xl/media/')) $embedded++;
    $worksheet = $zip->getFromName('xl/worksheets/sheet1.xml');
    $drawing = $zip->getFromName('xl/drawings/drawing1.xml');
    $xml = new DOMDocument();
    if (!$xml->loadXML($worksheet)) throw new RuntimeException('Worksheet XML invalid');
    $rows = $xml->getElementsByTagName('row')->length;
    $drawingXml = new DOMDocument();
    if (!$drawingXml->loadXML($drawing)) throw new RuntimeException('Drawing XML invalid');
    $expectedPhotos = array_sum(array_map(static fn ($record) => count($record['photos']), $records));
    if ($embedded !== $expectedPhotos) throw new RuntimeException('Photo count mismatch');
    $zip->close();
    $result = ['phase' => 'workbook', 'multiplier' => $multiplier, 'records' => count($records), 'photos' => $embedded,
        'sheetRows' => $rows, 'bytes' => filesize($target), 'writeSeconds' => $writeSeconds,
        'output' => basename($target), 'zipIntegrity' => 'PASS', 'worksheetXml' => 'PASS', 'drawingXml' => 'PASS'];
}
$result += ['totalSeconds' => (hrtime(true) - $start) / 1e9,
    'phpPeakMiB' => memory_get_peak_usage(true) / 1024 ** 2,
    'maxRssMiB' => getrusage()['ru_maxrss'] / 1024,
    'cpuUserSeconds' => getrusage()['ru_utime.tv_sec'] + getrusage()['ru_utime.tv_usec'] / 1e6];
file_put_contents($root . '/' . $mode . '-' . $multiplier . '.json', json_encode($result, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR));
echo json_encode($result, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR) . PHP_EOL;

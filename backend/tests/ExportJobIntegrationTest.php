<?php

use App\Core\Autoloader;
use App\Core\HttpException;
use App\Repositories\CompanyRepository;
use App\Repositories\OrganizationRepository;
use App\Repositories\RecordRepository;
use App\Repositories\UserRepository;
use App\Services\AdminService;
use App\Services\ExportImageService;
use App\Services\ExportJobService;
use App\Services\RecordService;

require dirname(__DIR__) . '/app/Core/Autoloader.php';
Autoloader::register(dirname(__DIR__) . '/app');
$database = (string) ($argv[1] ?? '');
if (!preg_match('/^xinyuan_test_[a-zA-Z0-9_]+$/', $database)) throw new RuntimeException('Isolated test database required.');
$pdo = new PDO("mysql:unix_socket=/var/run/mysqld/mysqld.sock;dbname={$database};charset=utf8mb4", 'root', '', [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES => false,
]);
$root = sys_get_temp_dir() . '/xinyuan-export-test-' . bin2hex(random_bytes(6));
mkdir($root, 0700);
mkdir($root . '/uploads', 0700);
$checks = 0;
$check = function (bool $condition, string $message) use (&$checks): void {
    if (!$condition) throw new RuntimeException($message);
    $checks++;
};
$reject = function (callable $call, int $status) use ($check): void {
    try { $call(); } catch (HttpException $error) { $check($error->status() === $status, $error->getMessage()); return; }
    throw new RuntimeException("Expected HTTP {$status}");
};
$remove = function (string $directory) use (&$remove): void {
    foreach (glob($directory . '/*') ?: [] as $path) { is_dir($path) ? $remove($path) : unlink($path); }
    rmdir($directory);
};
try {
    $companies = new CompanyRepository($pdo);
    $org = new OrganizationRepository($pdo);
    $users = new UserRepository($pdo);
    $records = new RecordRepository($pdo);
    $admin = new AdminService($companies, $org, $users);
    $company = $companies->create('导出任务测试', 'EXPORT_JOB_TEST');
    $company2 = $companies->create('导出任务其他公司', 'EXPORT_JOB_OTHER');
    $hq = ['id' => 0, 'role' => 'hq_admin', 'company_id' => $company];
    $makeUser = function (int $c, string $phone) use ($admin, $users, $hq): array {
        $id = $admin->createUser($hq, ['companyId' => $c, 'name' => '导出测试', 'phone' => $phone,
            'role' => 'inspector', 'loginScope' => 'both', 'password' => 'Only-Temporary-123']);
        return $users->findById($id);
    };
    $user = $makeUser($company, '19900003001');
    $other = $makeUser($company2, '19900003002');
    $makeRecord = function (array $who, string $name, array $photos = []) use ($records): int {
        return $records->create($who, ['customerName' => $name, 'customerPhone' => '0019900000000',
            'customerAddress' => '临时库', 'customerType' => '餐饮用户', 'inspectorName' => '测试',
            'deliveryPerson' => '测试', 'inspectionDate' => '2026-10-19', 'checkResult' => '隐患未清零',
            'hazardItems' => ['alarm_missing'], 'photos' => $photos]);
    };
    $im = imagecreatetruecolor(3200, 2400);
    mt_srand(42);
    for ($i = 0; $i < 12000; $i++) {
        $x = mt_rand(0, 3199); $y = mt_rand(0, 2399);
        imagefilledrectangle($im, $x, $y, $x + 30, $y + 30, imagecolorallocate($im, mt_rand(0,255), mt_rand(0,255), mt_rand(0,255)));
    }
    imagejpeg($im, $root . '/uploads/source.jpg', 96);
    imagedestroy($im);
    $sourceHash = hash_file('sha256', $root . '/uploads/source.jpg');
    $sourceBytes = filesize($root . '/uploads/source.jpg');
    $images = new ExportImageService($root . '/uploads', $root . '/images');
    $copy = $images->prepare('/uploads/source.jpg');
    $check($copy !== null, 'Photo compression succeeds');
    $size = getimagesize($copy['path']);
    $check(max($size[0], $size[1]) <= 1600, 'Compressed dimensions bounded');
    $check(filesize($copy['path']) < $sourceBytes / 2, 'Substantial synthetic image reduction');
    $check(hash_file('sha256', $root . '/uploads/source.jpg') === $sourceHash, 'Original byte hash unchanged');
    $check($images->prepare('/uploads/source.jpg')['path'] === $copy['path'], 'Cached copy reused');
    file_put_contents($root . '/private.jpg', file_get_contents($root . '/uploads/source.jpg'));
    $check($images->prepare('/uploads/../private.jpg') === null, 'Path traversal excluded');
    $check($images->prepare('/uploads/missing.jpg') === null, 'Missing photo recognized');
    file_put_contents($root . '/uploads/corrupt.jpg', 'not an image');
    $check($images->prepare('/uploads/corrupt.jpg') === null, 'Corrupt photo recognized');
    $small = imagecreatetruecolor(160, 80);
    imagejpeg($small, $root . '/uploads/rotate.jpg');
    imagedestroy($small);
    $jpeg = file_get_contents($root . '/uploads/rotate.jpg');
    $exif = "Exif\0\0II\x2a\0\x08\0\0\0\x01\0" . pack('vvVv', 0x112, 3, 1, 6) . "\0\0\0\0\0\0";
    file_put_contents($root . '/uploads/rotate.jpg', substr($jpeg, 0, 2) . "\xff\xe1" . pack('n', strlen($exif) + 2) . $exif . substr($jpeg, 2));
    $rotated = $images->prepare('/uploads/rotate.jpg');
    $dimensions = getimagesize($rotated['path']);
    $check($dimensions[0] === 80 && $dimensions[1] === 160, 'EXIF orientation preserved');

    $photo = ['filePath' => '/uploads/source.jpg'];
    $record1 = $makeRecord($user, 'PHOTO_ONE', ['storefront' => [$photo, $photo], 'hazard' => ['alarm_missing' => [$photo]]]);
    $record2 = $makeRecord($user, 'PHOTO_TWO', ['storefront' => [$photo], 'cylinder' => [['filePath' => '/uploads/missing.jpg']]]);
    $makeRecord($other, 'MUST_NOT_EXPORT', ['storefront' => [$photo]]);
    $jobs = new ExportJobService($pdo, $records, $root . '/jobs', $root . '/uploads', 1, 300, 41943040);
    $job = $jobs->create($user, ['hazardCode' => 'alarm_missing']);
    $check($job['total'] === 2 && $job['status'] === 'queued', 'Snapshot includes only owned records');
    $reject(fn () => $jobs->create($user, []), 409);
    $reject(fn () => $jobs->cancel($other, $job['id']), 404);
    $makeRecord($user, 'NEW_AFTER_SNAPSHOT');
    $check($jobs->workOnce(), 'Worker processes queued job');
    $done = $jobs->list($user)[0];
    $check($done['status'] === 'finished' && $done['processed'] === 2, 'Snapshot stable after new records');
    $check(count($done['files']) === 2, 'Automatic record-count splitting');
    $check($done['photos'] === 4 && $done['missingPhotos'] === 1, 'All photos counted with explicit missing warning');
    $token = bin2hex(random_bytes(32));
    $users->createSession((int) $user['id'], $token, 1, 'admin');
    $ticket = $jobs->ticket($user, $token, $job['id'], 1);
    $download = $jobs->redeem($ticket, $job['id'], 1);
    $check(is_file($download['path']), 'Native download ticket resolves');
    $reject(fn () => $jobs->ticket($other, $token, $job['id'], 1), 404);
    $reject(fn () => $jobs->redeem($ticket . 'x', $job['id'], 1), 403);
    $reject(fn () => $jobs->redeem($ticket, $job['id'], 2), 403);
    $reject(fn () => $jobs->ticket($user, $token, $job['id'], 99), 404);
    $warning = $jobs->redeem($jobs->ticket($user, $token, $job['id'], 0), $job['id'], 0);
    $check(str_contains(file_get_contents($warning['path']), (string) $record2), 'Warning file identifies affected record');
    $allText = '';
    $mediaCount = 0;
    $compressedBytes = 0;
    foreach ($done['files'] as $index => $file) {
        $path = $jobs->redeem($jobs->ticket($user, $token, $job['id'], $index + 1), $job['id'], $index + 1)['path'];
        $zip = new ZipArchive();
        $check($zip->open($path) === true, 'Part is valid XLSX');
        $sheet = $zip->getFromName('xl/worksheets/sheet1.xml');
        $allText .= $sheet;
        for ($n = 0; $n < $zip->numFiles; $n++) {
            $name = $zip->getNameIndex($n);
            if (str_starts_with($name, 'xl/media/')) {
                $mediaCount++;
                $content = $zip->getFromIndex($n);
                $compressedBytes += strlen($content);
                $imageSize = getimagesizefromstring($content);
                $check(max($imageSize[0], $imageSize[1]) <= 1600, 'Every embedded image compressed');
            }
        }
        $zip->close();
    }
    $check($mediaCount === 4, 'No dropped multi-photo entries');
    $check(str_contains($allText, '照片缺失') && str_contains($allText, '0019900000000'), 'Explicit missing-photo cells and text phone preservation');
    $check(!str_contains($allText, 'MUST_NOT_EXPORT') && !str_contains($allText, 'NEW_AFTER_SNAPSHOT'), 'Scope and snapshot exclusions in actual XLSX');
    $bits = explode('.', $ticket);
    $expiredData = json_decode(base64_decode($bits[0]), true);
    $expiredData['expires'] = time() - 1;
    $expiredPayload = base64_encode(json_encode($expiredData));
    $expiredTicket = $expiredPayload . '.' . hash_hmac('sha256', $expiredPayload, file_get_contents($root . '/jobs/download-secret'));
    $reject(fn () => $jobs->redeem($expiredTicket, $job['id'], 1), 403);
    $photoBoundJobs = new ExportJobService($pdo, $records, $root . '/jobs', $root . '/uploads', 1000, 2);
    $photoBound = $photoBoundJobs->create($user, ['keyword' => 'PHOTO_']);
    $photoBoundJobs->workOnce();
    $check(count($photoBoundJobs->list($user)[0]['files']) === 2, 'Photo count triggers a new part');
    $byteBoundJobs = new ExportJobService($pdo, $records, $root . '/jobs', $root . '/uploads', 1000, 300, 100);
    $byteBound = $byteBoundJobs->create($user, ['keyword' => 'PHOTO_']);
    $byteBoundJobs->workOnce();
    $check(count($byteBoundJobs->list($user)[0]['files']) === 2, 'Compressed image byte budget triggers a new part');
    $users->createSession((int) $user['id'], bin2hex(random_bytes(32)), 1, 'admin');
    $reject(fn () => $jobs->redeem($ticket, $job['id'], 1), 401);
    $pdo->prepare('UPDATE users SET can_view_company_records = 1 WHERE id = ?')->execute([$user['id']]);
    $reject(fn () => $jobs->ticket($user, $token, $job['id'], 1), 403);
    $pdo->prepare('UPDATE users SET can_view_company_records = 0 WHERE id = ?')->execute([$user['id']]);

    $cancel = $jobs->create($user, []);
    $jobs->cancel($user, $cancel['id']);
    $check(!$jobs->workOnce() && $jobs->list($user)[0]['status'] === 'cancelled', 'Cancellation prevents queued execution');
    $reject(fn () => $jobs->create($user, ['hazardCode' => 'not_exists']), 422);
    $reject(fn () => $jobs->create($user, ['keyword' => ['invalid']]), 422);
    $failed = $jobs->create($user, ['keyword' => 'NEW_AFTER_SNAPSHOT']);
    $pdo->prepare("DELETE FROM inspection_records WHERE creator_id = ? AND customer_name = 'NEW_AFTER_SNAPSHOT'")->execute([$user['id']]);
    $jobs->workOnce();
    $check($jobs->list($user)[0]['status'] === 'failed', 'Deleted snapshot record fails visibly');
    $interrupted = $jobs->create($user, []);
    $pdo->prepare("UPDATE export_jobs SET status = 'processing' WHERE id = ?")->execute([$interrupted['id']]);
    $jobs->recover();
    $check($jobs->list($user)[0]['status'] === 'failed', 'Worker interruption is recoverable and visible');

    // Actual export above the former cap, checking all IDs in real generated spreadsheets.
    $largeUser = $makeUser($company, '19900003003');
    $insert = $pdo->prepare("INSERT INTO inspection_records
        (company_id, creator_id, customer_name, customer_phone, customer_type, customer_address,
         inspector_name, delivery_person, inspection_date, check_result, status)
        VALUES (?, ?, ?, '0019900000000', '餐饮用户', '临时库', '测试', '测试', '2026-10-19', '隐患已清零', 'done')");
    $pdo->beginTransaction();
    for ($n = 1; $n <= 6001; $n++) $insert->execute([$company, $largeUser['id'], sprintf('BULK_%05d', $n)]);
    $pdo->commit();
    $largeJobs = new ExportJobService($pdo, $records, $root . '/jobs', $root . '/uploads');
    $large = $largeJobs->create($largeUser, []);
    $check($large['total'] === 6001, 'Snapshot exceeds 5000 without truncation');
    $started = microtime(true);
    $largeJobs->workOnce();
    $largeDone = $largeJobs->list($largeUser)[0];
    $check($largeDone['status'] === 'finished' && $largeDone['processed'] === 6001, 'All 6001 records processed');
    $check(count($largeDone['files']) === 7 && array_sum(array_column($largeDone['files'], 'records')) === 6001, 'All parts accounted for');
    $names = [];
    foreach ($largeDone['files'] as $file) {
        $zip = new ZipArchive();
        $zip->open($root . '/jobs/job-' . $large['id'] . '/' . $file['name']);
        preg_match_all('/BULK_\d{5}/', $zip->getFromName('xl/worksheets/sheet1.xml'), $matches);
        array_push($names, ...$matches[0]);
        $zip->close();
    }
    $check(count($names) === 6001 && count(array_unique($names)) === 6001, '6001 rows verified, no duplicates or omissions');
    $reject(fn () => (new RecordService($records, $org))->export($largeUser, []), 422);
    $pdo->prepare("UPDATE export_jobs SET updated_at = DATE_SUB(NOW(), INTERVAL 8 DAY) WHERE id = ?")->execute([$large['id']]);
    $largeJobs->cleanup();
    $check(!is_dir($root . '/jobs/job-' . $large['id']), 'Expired artifacts removed');
    $check($largeJobs->list($largeUser) === [], 'Expired jobs removed');

    $linkUser = $makeUser($company, '19900003991');
    $makeRecord($linkUser, 'LINK_ONLY', ['storefront' => [['filePath' => '/uploads/source.jpg'], ['filePath' => '/uploads/照片.jpg']],
        'hazard' => ['alarm_missing' => [['filePath' => '/uploads/missing.jpg']]],
        'cylinder' => [['filePath' => '/uploads/%2e%2e/secret.jpg']]]);
    $linkJobs = new ExportJobService($pdo, $records, $root . '/jobs', $root . '/uploads', baseUrl: 'https://www.gashbxy.com');
    $reject(fn () => $linkJobs->create($linkUser, ['photoMode' => 'unknown']), 422);
    $link = $linkJobs->create($linkUser, ['photoMode' => 'links', 'hazardCodes' => ['alarm_missing']]);
    $check($link['photoMode'] === 'links' && $link['total'] === 1, 'Link mode and filters preserved');
    $check($linkJobs->workOnce(), 'Link worker runs');
    $linkDone = $linkJobs->list($linkUser)[0];
    $check($linkDone['status'] === 'finished' && $linkDone['missingPhotos'] === 0, 'Link export skips image processing');
    $zip = new ZipArchive();
    $zip->open($root . '/jobs/job-' . $link['id'] . '/' . $linkDone['files'][0]['name']);
    $sheet = $zip->getFromName('xl/worksheets/sheet1.xml');
    $rels = $zip->getFromName('xl/worksheets/_rels/sheet1.xml.rels');
    $check(substr_count($sheet, '<hyperlink ') === 3, 'Every valid photo has own clickable link including repeated rows');
    $check(str_contains($rels, 'https://www.gashbxy.com/uploads/source.jpg') && str_contains($rels, '%E7%85%A7%E7%89%87.jpg'), 'Absolute URLs and Unicode paths encoded');
    $check(str_contains($rels, '/uploads/missing.jpg'), 'Link generation requires no file read');
    $check(!str_contains($rels, 'secret.jpg') && str_contains($sheet, '照片路径无效'), 'Traversal never becomes hyperlink');
    $check(!str_contains($sheet, '<f>') && !str_contains($sheet, '<drawing'), 'Native hyperlinks without formulas or images');
    for ($entry = 0; $entry < $zip->numFiles; $entry++) $check(!str_starts_with($zip->getNameIndex($entry), 'xl/media/'), 'No embedded image bytes');
    $sample = getenv('LINK_EXPORT_SAMPLE');
    if ($sample) copy($root . '/jobs/job-' . $link['id'] . '/' . $linkDone['files'][0]['name'], $sample);
    $zip->close();
    $largeLink = $linkJobs->create($largeUser, ['photoMode' => 'links']);
    $linkJobs->workOnce();
    $largeLinkDone = $linkJobs->list($largeUser)[0];
    $check($largeLinkDone['status'] === 'finished' && array_sum(array_column($largeLinkDone['files'], 'records')) === 6001, 'Link mode exports over 5000 records without truncation');
    $check(count($largeLinkDone['files']) === 1, '6001 link records stay in one workbook');
    $singleUser = $makeUser($company, '19900003992');
    for ($n = 0; $n < 2330; $n++) {
        $photos = array_fill(0, $n < 889 ? 4 : 3, ['filePath' => '/uploads/link-only.jpg']);
        $makeRecord($singleUser, 'SINGLE_' . $n, ['storefront' => $photos]);
    }
    $single = $linkJobs->create($singleUser, ['photoMode' => 'links']);
    $linkJobs->workOnce();
    $singleDone = $linkJobs->list($singleUser)[0];
    $check($singleDone['status'] === 'finished' && count($singleDone['files']) === 1, '2330 records and 7879 photo references produce one workbook');
    $check($singleDone['processed'] === 2330 && $singleDone['photos'] === 7879, 'Exact full record and photo totals');
    $zip = new ZipArchive();
    $zip->open($root . '/jobs/job-' . $single['id'] . '/' . $singleDone['files'][0]['name']);
    $singleSheet = $zip->getFromName('xl/worksheets/sheet1.xml');
    $check(substr_count($singleSheet, '<hyperlink ') === 7879, 'Every photo reference clickable in the single workbook');
    $zip->close();

    echo json_encode(['checks' => $checks, 'syntheticOriginalBytes' => $sourceBytes,
        'fourEmbeddedPhotosBytes' => $compressedBytes, 'largeExportRecords' => 6001,
        'largeExportSeconds' => round(microtime(true) - $started, 2), 'peakMemoryMiB' => round(memory_get_peak_usage(true) / 1048576, 1),
        'result' => 'PASS'], JSON_UNESCAPED_UNICODE) . "\n";
} finally {
    if ($pdo->inTransaction()) $pdo->rollBack();
    $remove($root);
}

<?php

namespace App\Services;

use App\Core\HttpException;
use App\Domain\RecordFilters;
use App\Repositories\RecordRepository;
use PDO;
use RuntimeException;
use Throwable;

final class ExportJobService
{
    public const FILTERS = ['status', 'dateFrom', 'dateTo', 'keyword', 'companyId', 'regionId', 'storeId',
        'inspectorName', 'deliveryPerson', 'customerType', 'checkResult', 'hazardCode',
        'customerName', 'customerPhone', 'customerAddress'];

    public function __construct(
        private PDO $pdo,
        private RecordRepository $records,
        private string $root,
        private string $uploads,
        private int $partRecords = 1000,
        private int $partPhotos = 300,
        private int $partBytes = 41943040,
        private string $baseUrl = ''
    ) {
        if (!is_dir($root) && !mkdir($root, 0700, true) && !is_dir($root)) throw new RuntimeException('无法创建导出目录。');
    }

    public function create(array $user, array $input): array
    {
        $photoMode = $input['photoMode'] ?? 'embedded';
        if (!in_array($photoMode, ['embedded', 'links'], true)) throw new HttpException(422, '导出方式无效。');
        if ($photoMode === 'links' && !preg_match('#^https?://[^/?\\s]+(?::\\d+)?$#', rtrim($this->baseUrl, '/'))) throw new HttpException(503, '照片链接地址尚未配置。');
        $filters = RecordFilters::normalize($input);
        $this->spaceCheck();
        $id = null;
        $stream = null;
        $this->pdo->exec('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ');
        $this->pdo->beginTransaction();
        try {
            // Serializes concurrent submissions by the same account.
            $lock = $this->pdo->prepare('SELECT id FROM users WHERE id = ? FOR UPDATE');
            $lock->execute([(int) $user['id']]);
            $active = $this->pdo->prepare("SELECT id FROM export_jobs WHERE requested_by = ? AND status IN ('queued','processing')");
            $active->execute([(int) $user['id']]);
            if ($active->fetch()) throw new HttpException(409, '已有导出任务正在处理，请在“数据导出”中查看进度。');
            $stmt = $this->pdo->prepare("INSERT INTO export_jobs (company_id, requested_by, export_type, filters_json)
                VALUES (?, ?, 'xlsx', ?)");
            $stmt->execute([$user['company_id'] ?: null, (int) $user['id'], '{}']);
            $id = (int) $this->pdo->lastInsertId();
            $directory = $this->directory($id);
            if (!mkdir($directory, 0700)) throw new RuntimeException('无法创建导出任务目录。');
            $stream = fopen($directory . '/ids.txt', 'xb');
            if (!$stream) throw new RuntimeException('无法保存导出范围。');
            $total = 0;
            foreach ($this->records->exportIds($user, $filters) as $recordId) {
                $line = $recordId . "\n";
                if (fwrite($stream, $line) !== strlen($line)) throw new RuntimeException('保存导出范围失败。');
                $total++;
            }
            fclose($stream);
            $stream = null;
            if (!$total) throw new HttpException(422, '当前筛选范围没有可导出的记录。');
            $meta = ['filters' => $filters, 'scope' => $this->scope($user), 'total' => $total, 'version' => 1, 'photoMode' => $photoMode];
            $this->pdo->prepare('UPDATE export_jobs SET filters_json = ? WHERE id = ?')
                ->execute([json_encode($meta, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR), $id]);
            $this->saveProgress($id, ['processed' => 0, 'total' => $total, 'photos' => 0, 'missingPhotos' => 0, 'files' => []]);
            $this->pdo->commit();
        } catch (Throwable $error) {
            if (is_resource($stream)) fclose($stream);
            $this->pdo->rollBack();
            if ($id) $this->removeDirectory($id);
            throw $error;
        }
        return $this->describe($this->owned($user, $id));
    }

    public function list(array $user): array
    {
        $stmt = $this->pdo->prepare("SELECT * FROM export_jobs WHERE requested_by = ? AND export_type = 'xlsx'
            ORDER BY id DESC LIMIT 30");
        $stmt->execute([(int) $user['id']]);
        return array_map(fn (array $job): array => $this->describe($job), $stmt->fetchAll());
    }

    public function cancel(array $user, int $id): void
    {
        $this->owned($user, $id);
        $this->pdo->prepare("UPDATE export_jobs SET status = 'cancelled' WHERE id = ? AND status IN ('queued','processing')")
            ->execute([$id]);
    }

    private function scope(array $user): string
    {
        return hash('sha256', json_encode([
            (int) $user['id'], (string) $user['role'], (int) $user['company_id'],
            (bool) ($user['can_view_company_records'] ?? false),
            (string) ($user['login_scope'] ?? 'both'), (string) ($user['status'] ?? 'active'),
        ]));
    }

    private function currentUser(array $job): array
    {
        $stmt = $this->pdo->prepare("SELECT * FROM users WHERE id = ? AND status = 'active'");
        $stmt->execute([(int) $job['requested_by']]);
        $user = $stmt->fetch();
        $meta = json_decode($job['filters_json'] ?? '{}', true);
        if (!$user || !hash_equals($meta['scope'] ?? '', $this->scope($user))) {
            throw new HttpException(403, '账号权限已变化，请重新提交导出任务。');
        }
        return $user;
    }

    private function owned(array $user, int $id): array
    {
        $stmt = $this->pdo->prepare("SELECT * FROM export_jobs WHERE id = ? AND requested_by = ? AND export_type = 'xlsx'");
        $stmt->execute([$id, (int) $user['id']]);
        $job = $stmt->fetch();
        if (!$job) throw new HttpException(404, '导出任务不存在或无权访问。');
        return $job;
    }

    private function describe(array $job): array
    {
        $progress = $this->progress((int) $job['id']);
        $meta = json_decode($job['filters_json'] ?? '{}', true);
        return [
            'photoMode' => $meta['photoMode'] ?? 'embedded',
            'id' => (int) $job['id'], 'status' => $job['status'], 'createdAt' => $job['created_at'],
            'processed' => (int) ($progress['processed'] ?? 0), 'total' => (int) ($meta['total'] ?? 0),
            'photos' => (int) ($progress['photos'] ?? 0), 'missingPhotos' => (int) ($progress['missingPhotos'] ?? 0),
            'error' => $job['error_message'] ?? '', 'filters' => $meta['filters'] ?? [],
            'expiresAt' => $progress['expiresAt'] ?? null,
            'files' => $job['status'] === 'finished' ? ($progress['files'] ?? []) : [],
        ];
    }

    /** Short lived download ticket bound to the current login session and exact part. */
    public function ticket(array $user, string $sessionToken, int $id, int $part): string
    {
        $job = $this->owned($user, $id);
        $this->downloadFile($job, $part);
        $payload = base64_encode(json_encode(['id' => $id, 'part' => $part, 'uid' => (int) $user['id'],
            'session' => hash('sha256', $sessionToken), 'expires' => time() + 120], JSON_THROW_ON_ERROR));
        return $payload . '.' . hash_hmac('sha256', $payload, $this->secret());
    }

    public function redeem(string $ticket, int $id, int $part): array
    {
        if (strlen($ticket) > 2048) throw new HttpException(403, '下载链接无效。');
        $bits = explode('.', $ticket);
        if (count($bits) !== 2 || !hash_equals(hash_hmac('sha256', $bits[0], $this->secret()), $bits[1])) {
            throw new HttpException(403, '下载链接无效。');
        }
        $data = json_decode(base64_decode($bits[0], true) ?: '', true);
        if (!is_array($data) || ($data['expires'] ?? 0) < time() || ($data['id'] ?? null) !== $id || ($data['part'] ?? null) !== $part) {
            throw new HttpException(403, '下载链接已过期，请重新点击下载。');
        }
        $stmt = $this->pdo->prepare("SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id
            WHERE s.token_hash = ? AND s.user_id = ? AND s.expires_at > NOW() AND u.status = 'active'");
        $stmt->execute([$data['session'] ?? '', (int) ($data['uid'] ?? 0)]);
        $user = $stmt->fetch();
        if (!$user) throw new HttpException(401, '登录已失效，请重新登录后下载。');
        return $this->downloadFile($this->owned($user, $id), $part);
    }

    private function downloadFile(array $job, int $part): array
    {
        $this->currentUser($job);
        $progress = $this->progress((int) $job['id']);
        if ($job['status'] !== 'finished' || ($progress['expiresAt'] ?? 0) < time()) {
            throw new HttpException(410, '文件尚未完成或已过期，请重新导出。');
        }
        if ($part === 0 && ($progress['missingPhotos'] ?? 0) > 0) {
            return ['path' => $this->directory((int) $job['id']) . '/photo-warnings.txt', 'name' => 'xinyuan-records-' . $job['id'] . '-photo-warnings.txt'];
        }
        $file = $progress['files'][$part - 1] ?? null;
        if (!$file || !preg_match('/^part-\d{4,}\.xlsx$/', $file['name'])) throw new HttpException(404, '分包不存在。');
        $path = $this->directory((int) $job['id']) . '/' . $file['name'];
        if (!is_file($path)) throw new HttpException(410, '导出文件已清理，请重新导出。');
        return ['path' => $path, 'name' => 'xinyuan-records-' . $job['id'] . '-' . $file['name']];
    }

    private function secret(): string
    {
        $path = $this->root . '/download-secret';
        if (!is_file($path)) {
            $stream = @fopen($path, 'x');
            if ($stream) {
                fwrite($stream, bin2hex(random_bytes(32)));
                fclose($stream);
                chmod($path, 0600);
            }
        }
        $secret = file_get_contents($path);
        if ($secret === false || strlen($secret) !== 64) throw new RuntimeException('导出下载配置不可用。');
        return $secret;
    }

    private function directory(int $id): string { return $this->root . '/job-' . $id; }
    private function progress(int $id): array
    {
        return json_decode(@file_get_contents($this->directory($id) . '/progress.json') ?: '{}', true) ?: [];
    }
    private function saveProgress(int $id, array $progress): void
    {
        $path = $this->directory($id) . '/progress.json';
        $json = json_encode($progress, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        if (file_put_contents($path . '.tmp', $json, LOCK_EX) !== strlen($json) || !rename($path . '.tmp', $path)) {
            throw new RuntimeException('导出进度保存失败，请检查磁盘空间。');
        }
    }
    private function spaceCheck(): void
    {
        $free = disk_free_space($this->root);
        if ($free !== false && $free < 1024 * 1024 * 1024) throw new HttpException(507, '服务器剩余空间不足，请联系管理员后重试。');
    }
    private function removeDirectory(int $id): void
    {
        foreach (glob($this->directory($id) . '/*') ?: [] as $path) if (is_file($path)) @unlink($path);
        if (is_dir($this->directory($id))) @rmdir($this->directory($id));
    }

    /** Called only while holding the worker lock, after a process restart. */
    public function recover(): void
    {
        $this->pdo->exec("UPDATE export_jobs SET status = 'failed', error_message = '后台处理曾中断，请重新提交导出。'
            WHERE export_type = 'xlsx' AND status = 'processing'");
    }

    public function cleanup(): void
    {
        $jobs = $this->pdo->query("SELECT id FROM export_jobs WHERE export_type = 'xlsx'
            AND status IN ('finished','failed','cancelled') AND updated_at < DATE_SUB(NOW(), INTERVAL 7 DAY)")->fetchAll();
        foreach ($jobs as $job) {
            $this->removeDirectory((int) $job['id']);
            $this->pdo->prepare('DELETE FROM export_jobs WHERE id = ?')->execute([(int) $job['id']]);
        }
        foreach (glob($this->root . '/images/*') ?: [] as $file) {
            if (is_file($file) && filemtime($file) < time() - 30 * 86400) @unlink($file);
        }
    }

    /** Exactly one worker owns this queue (CLI holds flock). */
    public function workOnce(): bool
    {
        $job = $this->pdo->query("SELECT * FROM export_jobs WHERE export_type = 'xlsx' AND status = 'queued' ORDER BY id LIMIT 1")->fetch();
        if (!$job) return false;
        $id = (int) $job['id'];
        $claim = $this->pdo->prepare("UPDATE export_jobs SET status = 'processing' WHERE id = ? AND status = 'queued'");
        $claim->execute([$id]);
        if (!$claim->rowCount()) return true;
        $input = null;
        $warnings = null;
        try {
            $user = $this->currentUser($job);
            $progress = $this->progress($id);
            if (!isset($progress['total'])) throw new RuntimeException('导出范围文件缺失，请重新提交。');
            $input = fopen($this->directory($id) . '/ids.txt', 'rb');
            $warnings = fopen($this->directory($id) . '/photo-warnings.txt', 'wb');
            if (!$input || !$warnings) throw new RuntimeException('导出任务文件不可读写。');
            $meta = json_decode($job['filters_json'] ?? '{}', true);
            $links = ($meta['photoMode'] ?? 'embedded') === 'links';
            $images = new ExportImageService($this->uploads, $this->root . '/images');
            $export = new ExportService($this->uploads, $images, $this->directory($id), $links ? $this->baseUrl : '');
            $part = [];
            $photoCount = 0;
            $bytes = 0;
            $flush = function () use (&$part, &$photoCount, &$bytes, &$progress, $export, $id): void {
                if (!$part) return;
                $this->spaceCheck();
                $this->assertRunning($id);
                $offset = array_sum(array_column($progress['files'], 'records'));
                $file = $export->recordsWorkbookFile($part, $offset);
                $name = sprintf('part-%04d.xlsx', count($progress['files']) + 1);
                $target = $this->directory($id) . '/' . $name;
                if (!rename($file, $target)) throw new RuntimeException('无法保存导出分包。');
                $progress['files'][] = ['name' => $name, 'records' => count($part), 'bytes' => filesize($target),
                    'firstSequence' => $offset + 1, 'lastSequence' => $offset + count($part)];
                $part = [];
                $photoCount = 0;
                $bytes = 0;
                $this->saveProgress($id, $progress);
            };
            while (!feof($input)) {
                $this->assertRunning($id);
                $this->currentUser($job);
                $ids = [];
                while (count($ids) < 50 && ($line = fgets($input)) !== false) {
                    if (!preg_match('/^\d+\n?$/', $line)) throw new RuntimeException('导出记录范围异常。');
                    $ids[] = (int) trim($line);
                }
                if (!$ids) break;
                $batch = $this->records->exportBatch($user, $ids);
                if (count($batch) !== count($ids)) throw new HttpException(409, '部分记录已删除或不可访问，请重新提交导出，避免遗漏。');
                foreach ($batch as $record) {
                    $this->assertRunning($id);
                    $this->spaceCheck();
                    $recordBytes = 0;
                    $recordPhotos = count($record['photos']);
                    if ($links) $progress['photos'] += $recordPhotos;
                    foreach ($links ? [] : $record['photos'] as $photoIndex => $photo) {
                        if ($photoIndex % 10 === 0) {
                            $this->assertRunning($id);
                            $this->spaceCheck();
                            $this->saveProgress($id, $progress);
                        }
                        $copy = $images->prepare((string) $photo['file_path']);
                        if ($copy) {
                            $recordBytes += filesize($copy['path']);
                            $progress['photos']++;
                        } else {
                            $progress['missingPhotos']++;
                            $line = '记录 ' . $record['id'] . '：照片 ' . $photo['id'] . " 缺失、格式不支持或超过处理尺寸，请核对原图。\n";
                            if (fwrite($warnings, $line) !== strlen($line)) throw new RuntimeException('无法保存照片异常说明。');
                        }
                    }
                    // Link-only exports use a single workbook for ordinary datasets.
                    // Keep a generous estimated-row budget to bound PHP memory for exceptional volumes.
                    $split = $links
                        ? count($part) + $photoCount + 1 + $recordPhotos > 20000
                        : (count($part) >= $this->partRecords || $photoCount + $recordPhotos > $this->partPhotos || $bytes + $recordBytes > $this->partBytes);
                    if ($part && $split) $flush();
                    $part[] = $record;
                    $photoCount += $recordPhotos;
                    $bytes += $recordBytes;
                    $progress['processed']++;
                    if ($progress['processed'] % 10 === 0) $this->saveProgress($id, $progress);
                }
            }
            $flush();
            if ($progress['processed'] !== $progress['total']) throw new RuntimeException('导出条数与范围不一致，请重新提交。');
            $this->currentUser($job);
            $this->assertRunning($id);
            $progress['expiresAt'] = time() + 7 * 86400;
            $this->saveProgress($id, $progress);
            $this->pdo->prepare("UPDATE export_jobs SET status = 'finished' WHERE id = ? AND status = 'processing'")->execute([$id]);
        } catch (Throwable $error) {
            $message = $error instanceof HttpException ? $error->getMessage() : '生成失败，请重试；如仍失败，请联系管理员查看导出日志。';
            error_log('Export job ' . $id . ': ' . $error->getMessage());
            $this->pdo->prepare("UPDATE export_jobs SET status = 'failed', error_message = ? WHERE id = ? AND status = 'processing'")
                ->execute([$message, $id]);
        } finally {
            if (is_resource($input)) fclose($input);
            if (is_resource($warnings)) fclose($warnings);
        }
        return true;
    }

    private function assertRunning(int $id): void
    {
        $stmt = $this->pdo->prepare('SELECT status FROM export_jobs WHERE id = ?');
        $stmt->execute([$id]);
        if ($stmt->fetchColumn() !== 'processing') throw new HttpException(409, '导出已取消。');
    }
}

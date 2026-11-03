<?php

use App\Core\Autoloader;
use App\Core\Config;
use App\Core\Database;
use App\Core\Env;
use App\Repositories\RecordRepository;
use App\Services\ExportJobService;

require dirname(__DIR__) . '/app/Core/Autoloader.php';
Autoloader::register(dirname(__DIR__) . '/app');
Env::load(dirname(__DIR__) . '/.env');
$config = new Config(require dirname(__DIR__) . '/config/app.php');
$root = (string) $config->get('export_root');
if (!is_dir($root)) mkdir($root, 0700, true);
$lock = fopen($root . '/worker.lock', 'c');
if (!$lock || !flock($lock, LOCK_EX | LOCK_NB)) exit(0);
$pdo = (new Database($config))->pdo();
$service = new ExportJobService($pdo, new RecordRepository($pdo), $root, (string) $config->get('upload_root'), baseUrl: (string) $config->get('base_url'));
$service->recover();
$lastCleanup = 0;
do {
    if (time() - $lastCleanup > 3600) {
        $service->cleanup();
        $lastCleanup = time();
    }
    $worked = $service->workOnce();
    if (in_array('--once', $argv, true)) break;
    if (!$worked) sleep(3);
} while (true);

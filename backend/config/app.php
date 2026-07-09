<?php

return [
    'env' => getenv('APP_ENV') ?: 'local',
    'debug' => (getenv('APP_DEBUG') ?: 'false') === 'true',
    'base_url' => getenv('APP_BASE_URL') ?: 'http://127.0.0.1',
    'session_days' => (int) (getenv('SESSION_DAYS') ?: 7),
    'upload_root' => getenv('UPLOAD_ROOT') ?: dirname(__DIR__) . '/storage/uploads',
    'database' => [
        'host' => getenv('DB_HOST') ?: '127.0.0.1',
        'port' => getenv('DB_PORT') ?: '3306',
        'database' => getenv('DB_DATABASE') ?: 'xinyuan_gas',
        'username' => getenv('DB_USERNAME') ?: 'root',
        'password' => getenv('DB_PASSWORD') ?: '',
        'charset' => 'utf8mb4',
    ],
];


<?php

require dirname(__DIR__) . '/app/Core/Autoloader.php';

use App\Core\Autoloader;
use App\Domain\UserAccess;

Autoloader::register(dirname(__DIR__) . '/app');

function assertSameValue(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        fwrite(STDERR, $message . PHP_EOL);
        fwrite(STDERR, 'Expected: ' . var_export($expected, true) . PHP_EOL);
        fwrite(STDERR, 'Actual: ' . var_export($actual, true) . PHP_EOL);
        exit(1);
    }
}

assertSameValue(['both', 'admin'], UserAccess::allowedLoginScopes('hq_admin'), '总部管理员应可登录 APP 和后台。');
assertSameValue(['both', 'admin'], UserAccess::allowedLoginScopes('company_admin'), '公司管理员应可登录 APP 和后台。');
assertSameValue(['both', 'app', 'admin'], UserAccess::allowedLoginScopes('inspector'), '检查员登录端配置应保持兼容。');

assertSameValue('all', UserAccess::recordScope(['role' => 'hq_admin']), '总部管理员应查看全部公司记录。');
assertSameValue('company', UserAccess::recordScope(['role' => 'company_admin']), '公司管理员应查看本公司记录。');
assertSameValue(
    'company',
    UserAccess::recordScope(['role' => 'inspector', 'can_view_company_records' => 1]),
    '获授权检查员应查看本公司全部记录。'
);
assertSameValue(
    'own',
    UserAccess::recordScope(['role' => 'inspector', 'can_view_company_records' => 0]),
    '未授权检查员仍应只查看本人记录。'
);

echo "UserAccessTest ok\n";

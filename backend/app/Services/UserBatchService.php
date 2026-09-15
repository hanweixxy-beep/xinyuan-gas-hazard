<?php

namespace App\Services;

use App\Core\HttpException;
use App\Repositories\UserRepository;

final class UserBatchService
{
    private const FIELDS = [
        'companyCode' => '公司编码', 'name' => '姓名', 'phone' => '手机号', 'role' => '角色',
        'loginScope' => '允许登录端', 'canViewCompanyRecords' => '查看本公司全部记录', 'password' => '初始密码',
    ];
    private const ROLES = ['检查员' => 'inspector', '公司管理员' => 'company_admin', '总部管理员' => 'hq_admin'];
    private const SCOPES = ['APP+后台' => 'both', '仅APP' => 'app', '仅后台' => 'admin'];
    private const MAX_ROWS = 200;

    public function __construct(private AdminService $admin, private UserRepository $users)
    {
    }

    /** @param array<string,mixed> $actor @return array<string,mixed> */
    public function options(array $actor): array
    {
        $companies = array_values(array_filter($this->admin->companies($actor), static fn (array $company): bool => $company['status'] === 'active'));
        return [
            'version' => 1, 'sheetName' => '批量创建用户', 'maxRows' => self::MAX_ROWS,
            'fields' => array_map(static fn (string $key, string $label): array => ['key' => $key, 'label' => $label], array_keys(self::FIELDS), array_values(self::FIELDS)),
            'roles' => $actor['role'] === 'hq_admin' ? array_keys(self::ROLES) : ['检查员'],
            'loginScopes' => array_keys(self::SCOPES),
            'companies' => array_map(static fn (array $company): array => ['id' => (int) $company['id'], 'code' => $company['code'], 'name' => $company['name']], $companies),
        ];
    }

    /** @param array<string,mixed> $actor @param array<mixed> $rows @return array<string,mixed> */
    public function preview(array $actor, array $rows): array
    {
        return $this->analyze($actor, $rows)['preview'];
    }

    /** @param array<string,mixed> $actor @param array<mixed> $rows @return array<string,mixed> */
    public function create(array $actor, array $rows): array
    {
        $analysis = $this->analyze($actor, $rows);
        if ($analysis['preview']['errorCount'] > 0) {
            $errors = array_filter($analysis['preview']['rows'], static fn (array $row): bool => count($row['errors']) > 0);
            $messages = array_map(static fn (array $row): string => '第 ' . $row['rowNumber'] . ' 行：' . implode('；', $row['errors']), array_slice(array_values($errors), 0, 5));
            throw new HttpException(422, '本批次未创建账号。' . implode(' ', $messages));
        }
        $prepared = $analysis['prepared'];
        // Hash before opening the transaction, and never return plaintext passwords.
        foreach ($prepared as &$user) {
            $user['password_hash'] = password_hash($user['password'], PASSWORD_DEFAULT);
            unset($user['password']);
        }
        unset($user);
        $ids = $this->users->createUsersAtomically($prepared);
        return ['createdCount' => count($ids)];
    }

    /** @param array<string,mixed> $actor @param array<mixed> $rows @return array<string,mixed> */
    private function analyze(array $actor, array $rows): array
    {
        $options = $this->options($actor);
        if (!array_is_list($rows) || count($rows) < 1 || count($rows) > self::MAX_ROWS) {
            throw new HttpException(422, '每批须包含 1-200 个用户。');
        }
        $companies = array_column($options['companies'], null, 'code');
        $previews = [];
        $prepared = [];
        $seenPhones = [];
        $seenRows = [];
        foreach ($rows as $raw) {
            if (!is_array($raw) || !is_int($raw['rowNumber'] ?? null) || $raw['rowNumber'] < 2 || $raw['rowNumber'] > 201 || isset($seenRows[$raw['rowNumber']])) {
                throw new HttpException(422, '表格行号不正确，请重新下载固定模板。');
            }
            $rowNumber = $raw['rowNumber'];
            $seenRows[$rowNumber] = true;
            foreach (self::FIELDS as $key => $label) {
                if (!is_string($raw[$key] ?? null) || strlen($raw[$key]) > 2000) {
                    throw new HttpException(422, "第 {$rowNumber} 行 {$label} 格式不正确。");
                }
            }
            $companyCode = strtoupper(trim($raw['companyCode']));
            $company = $companies[$companyCode] ?? null;
            $phone = trim($raw['phone']);
            $roleLabel = trim($raw['role']) ?: '检查员';
            $scopeLabel = trim($raw['loginScope']) ?: 'APP+后台';
            $scope = self::SCOPES[$scopeLabel] ?? null;
            $role = self::ROLES[$roleLabel] ?? null;
            $viewLabel = trim($raw['canViewCompanyRecords']) ?: '否';
            $errors = [];
            if (!$company) $errors[] = '公司编码不存在、已停用或不在管理权限内';
            if (!$role) $errors[] = '角色须为检查员、公司管理员或总部管理员';
            if (!$scope) $errors[] = '允许登录端须为 APP+后台、仅APP 或仅后台';
            if (!in_array($viewLabel, ['是', '否'], true)) $errors[] = '查看本公司全部记录须填写是或否';
            if ($phone !== '') {
                if (isset($seenPhones[$phone])) {
                    $first = $seenPhones[$phone];
                    $errors[] = '手机号与第 ' . $previews[$first]['rowNumber'] . ' 行重复';
                    $previews[$first]['errors'][] = "手机号与第 {$rowNumber} 行重复";
                } else {
                    $seenPhones[$phone] = count($previews);
                }
            }
            if ($company && $role && $scope && in_array($viewLabel, ['是', '否'], true)) {
                try {
                    $data = $this->admin->validateNewUser($actor, [
                        'companyId' => $company['id'], 'name' => $raw['name'], 'phone' => $phone,
                        'role' => $role, 'loginScope' => $scope, 'canViewCompanyRecords' => $viewLabel === '是', 'password' => $raw['password'],
                    ]);
                    $data['rowNumber'] = $rowNumber;
                    $prepared[] = $data;
                } catch (HttpException $exception) {
                    $errors[] = $exception->getMessage();
                }
            }
            $previews[] = [
                'rowNumber' => $rowNumber, 'companyCode' => $companyCode, 'companyName' => $company['name'] ?? '',
                'name' => trim($raw['name']), 'phone' => $phone, 'role' => $roleLabel,
                'loginScope' => $scopeLabel, 'canViewCompanyRecords' => $role === 'inspector' ? $viewLabel : '管理角色权限',
                'errors' => $errors,
            ];
        }
        $errorCount = count(array_filter($previews, static fn (array $row): bool => count($row['errors']) > 0));
        return ['prepared' => $prepared, 'preview' => [
            'total' => count($previews), 'validCount' => count($previews) - $errorCount, 'errorCount' => $errorCount, 'rows' => $previews,
        ]];
    }
}

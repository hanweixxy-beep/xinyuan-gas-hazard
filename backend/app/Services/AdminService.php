<?php

namespace App\Services;

use App\Core\HttpException;
use App\Domain\UserAccess;
use App\Repositories\CompanyRepository;
use App\Repositories\OrganizationRepository;
use App\Repositories\UserRepository;

final class AdminService
{
    private const ROLES = ['hq_admin', 'company_admin', 'inspector'];
    private const STATUSES = ['active', 'disabled'];

    public function __construct(
        private CompanyRepository $companies,
        private OrganizationRepository $organization,
        private UserRepository $users
    ) {
    }

    /** @param array<string, mixed> $actor @return array{regions:list<array<string,mixed>>,stores:list<array<string,mixed>>} */
    public function organization(array $actor): array
    {
        $this->requireManager($actor);
        $companyId = $actor['role'] === 'hq_admin' ? null : (int) $actor['company_id'];
        return [
            'regions' => $this->organization->listRegions($companyId),
            'stores' => $this->organization->listStores($companyId),
        ];
    }

    /** @param array<string, mixed> $actor @param array<string, mixed> $payload */
    public function createRegion(array $actor, array $payload): int
    {
        $this->requireManager($actor);
        $companyId = $this->resolveCompanyId($actor, $payload);
        $name = trim((string) ($payload['name'] ?? ''));
        if ($name === '' || mb_strlen($name) > 80) {
            throw new HttpException(422, '片区名称不能为空，且不能超过 80 个字符。');
        }
        if ($this->organization->regionNameExists($companyId, $name)) {
            throw new HttpException(422, '该公司已存在同名片区。');
        }
        return $this->organization->createRegion($companyId, $name);
    }

    /** @param array<string, mixed> $actor @param array<string, mixed> $payload */
    public function createStore(array $actor, array $payload): int
    {
        $this->requireManager($actor);
        $regionId = (int) ($payload['regionId'] ?? 0);
        $region = $this->organization->findRegion($regionId);
        if (!$region || $region['status'] !== 'active') {
            throw new HttpException(422, '请选择有效且已启用的片区。');
        }
        $this->assertCompanyAccessible($actor, (int) $region['company_id']);
        $name = trim((string) ($payload['name'] ?? ''));
        $address = trim((string) ($payload['address'] ?? ''));
        if ($name === '' || mb_strlen($name) > 120) {
            throw new HttpException(422, '门店名称不能为空，且不能超过 120 个字符。');
        }
        if (mb_strlen($address) > 255) {
            throw new HttpException(422, '门店地址不能超过 255 个字符。');
        }
        if ($this->organization->storeNameExists($regionId, $name)) {
            throw new HttpException(422, '该片区已存在同名门店。');
        }
        return $this->organization->createStore((int) $region['company_id'], $regionId, $name, $address);
    }

    /** @param array<string, mixed> $actor */
    public function updateRegionStatus(array $actor, int $id, string $status): void
    {
        $this->requireManager($actor);
        $this->assertStatus($status);
        $region = $this->organization->findRegion($id);
        if (!$region) {
            throw new HttpException(404, '片区不存在。');
        }
        $this->assertCompanyAccessible($actor, (int) $region['company_id']);
        $this->organization->updateRegionStatus($id, $status);
    }

    /** @param array<string, mixed> $actor */
    public function updateStoreStatus(array $actor, int $id, string $status): void
    {
        $this->requireManager($actor);
        $this->assertStatus($status);
        $store = $this->organization->findStore($id);
        if (!$store) {
            throw new HttpException(404, '门店不存在。');
        }
        $this->assertCompanyAccessible($actor, (int) $store['company_id']);
        if ($status === 'active' && $store['region_status'] !== 'active') {
            throw new HttpException(422, '请先启用门店所属片区。');
        }
        $this->organization->updateStoreStatus($id, $status);
    }

    /** @param array<string, mixed> $actor @return list<array<string, mixed>> */
    public function companies(array $actor): array
    {
        $this->requireManager($actor);
        if ($actor['role'] === 'hq_admin') {
            return $this->companies->listAll();
        }

        $company = $this->companies->find((int) $actor['company_id']);
        return $company ? [$company] : [];
    }

    /** @param array<string, mixed> $actor @param array<string, mixed> $payload */
    public function createCompany(array $actor, array $payload): int
    {
        $this->requireHq($actor);
        $name = trim((string) ($payload['name'] ?? ''));
        $code = strtoupper(trim((string) ($payload['code'] ?? '')));

        if ($name === '' || mb_strlen($name) > 120) {
            throw new HttpException(422, '公司名称不能为空，且不能超过 120 个字符。');
        }
        if (!preg_match('/^[A-Z0-9_-]{2,32}$/', $code)) {
            throw new HttpException(422, '公司编码只能使用 2-32 位字母、数字、下划线或短横线。');
        }
        if ($this->companies->findByCode($code)) {
            throw new HttpException(422, '公司编码已存在，请换一个。');
        }

        return $this->companies->create($name, $code);
    }

    /** @param array<string, mixed> $actor @return list<array<string, mixed>> */
    public function users(array $actor): array
    {
        $this->requireManager($actor);
        return $this->users->listForAdmin($actor);
    }

    /** @param array<string, mixed> $actor @param array<string, mixed> $payload */
    public function createUser(array $actor, array $payload): int
    {
        $this->requireManager($actor);
        $name = trim((string) ($payload['name'] ?? ''));
        $phone = trim((string) ($payload['phone'] ?? ''));
        $role = (string) ($payload['role'] ?? '');
        $loginScope = (string) ($payload['loginScope'] ?? UserAccess::defaultLoginScope($role));
        $canViewCompanyRecords = $role === 'inspector' && !empty($payload['canViewCompanyRecords']);
        $password = (string) ($payload['password'] ?? '');
        $companyId = $this->resolveCompanyId($actor, $payload);

        if ($name === '' || mb_strlen($name) > 80) {
            throw new HttpException(422, '姓名不能为空，且不能超过 80 个字符。');
        }
        if (!preg_match('/^[0-9]{6,20}$/', $phone)) {
            throw new HttpException(422, '手机号格式不正确，请输入 6-20 位数字。');
        }
        if (!in_array($role, self::ROLES, true)) {
            throw new HttpException(422, '用户角色不正确。');
        }
        if (!in_array($loginScope, UserAccess::allowedLoginScopes($role), true)) {
            throw new HttpException(422, '登录权限不正确。');
        }
        if (mb_strlen($password) < 8) {
            throw new HttpException(422, '初始密码至少 8 位。');
        }
        if ($this->users->findByPhone($phone)) {
            throw new HttpException(422, '手机号已存在，不能重复创建。');
        }

        $this->assertRoleAssignable($actor, $role);
        return $this->users->createUser([
            'company_id' => $companyId,
            'region_id' => null,
            'store_id' => null,
            'name' => $name,
            'phone' => $phone,
            'password_hash' => password_hash($password, PASSWORD_DEFAULT),
            'role' => $role,
            'login_scope' => $loginScope,
            'can_view_company_records' => $canViewCompanyRecords ? 1 : 0,
        ]);
    }

    /** @param array<string, mixed> $actor @param array<string, mixed> $payload */
    public function updateUserAccess(array $actor, int $id, array $payload): void
    {
        $this->requireManager($actor);
        if ((int) $actor['id'] === $id) {
            throw new HttpException(422, '当前登录账号不能在此处修改自身权限。');
        }
        $target = $this->requireTargetUser($actor, $id);
        $this->assertTargetManageable($actor, $target);

        $role = (string) $target['role'];
        $loginScope = (string) ($payload['loginScope'] ?? $target['login_scope'] ?? UserAccess::defaultLoginScope($role));
        if (!in_array($loginScope, UserAccess::allowedLoginScopes($role), true)) {
            throw new HttpException(422, '登录权限不正确。');
        }

        $canViewCompanyRecords = $role === 'inspector' && !empty($payload['canViewCompanyRecords']);
        $this->users->updateAccess(
            $id,
            $loginScope,
            $canViewCompanyRecords
        );
        $this->users->deleteSessionsForUser($id);
    }

    /** @param array<string, mixed> $actor */
    public function updateUserStatus(array $actor, int $id, string $status): void
    {
        $this->requireManager($actor);
        if (!in_array($status, self::STATUSES, true)) {
            throw new HttpException(422, '用户状态不正确。');
        }
        if ((int) $actor['id'] === $id) {
            throw new HttpException(422, '不能停用或启用当前登录账号。');
        }

        $target = $this->requireTargetUser($actor, $id);
        $this->assertTargetManageable($actor, $target);
        $this->users->updateStatus($id, $status);
        if ($status === 'disabled') {
            $this->users->deleteSessionsForUser($id);
        }
    }

    /** @param array<string, mixed> $actor */
    public function resetPassword(array $actor, int $id, string $password): void
    {
        $this->requireManager($actor);
        if ((int) $actor['id'] === $id) {
            throw new HttpException(422, '当前登录账号请使用“修改密码”。');
        }
        if (mb_strlen($password) < 8) {
            throw new HttpException(422, '新密码至少 8 位。');
        }

        $target = $this->requireTargetUser($actor, $id);
        $this->assertTargetManageable($actor, $target);
        $this->users->updatePassword($id, password_hash($password, PASSWORD_DEFAULT));
        $this->users->deleteSessionsForUser($id);
    }

    /** @param array<string, mixed> $actor */
    private function requireManager(array $actor): void
    {
        if (!in_array($actor['role'], ['hq_admin', 'company_admin'], true)) {
            throw new HttpException(403, '只有管理员可以访问账号管理。');
        }
    }

    /** @param array<string, mixed> $actor */
    private function requireHq(array $actor): void
    {
        if ($actor['role'] !== 'hq_admin') {
            throw new HttpException(403, '只有总部管理员可以执行此操作。');
        }
    }

    private function assertStatus(string $status): void
    {
        if (!in_array($status, self::STATUSES, true)) {
            throw new HttpException(422, '状态不正确。');
        }
    }

    /** @param array<string, mixed> $actor */
    private function assertCompanyAccessible(array $actor, int $companyId): void
    {
        if ($actor['role'] !== 'hq_admin' && (int) $actor['company_id'] !== $companyId) {
            throw new HttpException(403, '不能管理其他公司的组织。');
        }
    }

    /** @param array<string, mixed> $actor @param array<string, mixed> $payload */
    private function resolveCompanyId(array $actor, array $payload): int
    {
        if ($actor['role'] === 'company_admin') {
            return (int) $actor['company_id'];
        }

        $companyId = (int) ($payload['companyId'] ?? 0);
        if ($companyId <= 0 || !$this->companies->find($companyId)) {
            throw new HttpException(422, '请选择有效公司。');
        }
        return $companyId;
    }

    /** @param array<string, mixed> $actor */
    private function assertRoleAssignable(array $actor, string $role): void
    {
        if ($actor['role'] === 'company_admin' && $role !== 'inspector') {
            throw new HttpException(403, '公司管理员只能创建检查员账号。');
        }
    }

    /** @param array<string, mixed> $actor @return array<string, mixed> */
    private function requireTargetUser(array $actor, int $id): array
    {
        $target = $this->users->findById($id);
        if (!$target) {
            throw new HttpException(404, '用户不存在。');
        }
        if ($actor['role'] === 'company_admin' && (int) $target['company_id'] !== (int) $actor['company_id']) {
            throw new HttpException(403, '不能管理其他公司的用户。');
        }
        return $target;
    }

    /** @param array<string, mixed> $actor @param array<string, mixed> $target */
    private function assertTargetManageable(array $actor, array $target): void
    {
        if ($actor['role'] === 'company_admin' && $target['role'] !== 'inspector') {
            throw new HttpException(403, '公司管理员只能管理检查员账号。');
        }
    }
}

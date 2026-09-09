<?php

namespace App\Services;

use App\Core\HttpException;
use App\Domain\HazardCatalog;
use App\Repositories\OrganizationRepository;
use App\Repositories\RecordRepository;

final class RecordService
{
    public function __construct(
        private RecordRepository $records,
        private OrganizationRepository $organization
    )
    {
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $filters @return list<array<string, mixed>> */
    public function list(array $user, array $filters = []): array
    {
        return $this->records->listForUser($user, $filters);
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $filters @return list<array<string, mixed>> */
    public function export(array $user, array $filters = []): array
    {
        $records = $this->records->listForUser($user, $filters, 5000);
        return array_map(fn (array $record): array => $this->show((int) $record['id'], $user), $records);
    }

    /** @param array<string, mixed> $user @return array<string, mixed> */
    public function show(int $id, array $user): array
    {
        $record = $this->records->findForUser($id, $user);
        if (!$record) {
            throw new HttpException(404, '记录不存在，或你没有权限查看。');
        }
        return $record;
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    public function create(array $user, array $payload): int
    {
        // Legacy APP imports can lack organization fields; new inspections use submitDraft.
        $payload = $this->withOrganizationSelection($user, $payload);
        $this->validateCreate($payload);
        return $this->records->create($user, $payload);
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    public function saveDraft(array $user, array $payload): int
    {
        $payload = $this->withOrganizationSelection($user, $payload);
        $this->validateDraft($payload);
        return $this->records->saveDraft($user, $payload);
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    public function submitDraft(array $user, array $payload): int
    {
        $payload = $this->withOrganizationSelection($user, $payload, true);
        $this->validateCreate($payload);
        $this->validateCloudSubmission($payload);
        $this->records->saveDraft($user, $payload);
        return $this->records->submitDraftByClientUuid((string) $payload['clientUuid'], $user);
    }

    /** @param array<string, mixed> $user @return array<string, mixed> */
    public function organizationOptions(array $user): array
    {
        $companyId = (int) ($user['company_id'] ?? 0);
        if ($companyId <= 0) {
            throw new HttpException(422, '当前账号未分配公司，无法选择检查归属。');
        }

        $regions = array_map(static fn (array $region): array => [
            'id' => (int) $region['id'],
            'name' => (string) $region['name'],
        ], $this->organization->listActiveRegions($companyId));
        $stores = array_map(static fn (array $store): array => [
            'id' => (int) $store['id'],
            'regionId' => (int) $store['region_id'],
            'name' => (string) $store['name'],
            'address' => (string) ($store['address'] ?? ''),
        ], $this->organization->listActiveStores($companyId));

        return [
            'companyId' => $companyId,
            'companyName' => (string) ($user['company_name'] ?? ''),
            'regions' => $regions,
            'stores' => $stores,
            'default' => [
                'regionId' => null,
                'regionName' => '',
                'storeId' => null,
                'storeName' => '',
            ],
        ];
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    public function rectify(int $id, array $user, array $payload): void
    {
        if (empty($payload['rectificationDate'])) {
            throw new HttpException(422, '请填写整改日期。');
        }
        $this->records->rectify($id, $user, $payload);
    }

    /** @param array<string, mixed> $payload */
    private function validateCreate(array $payload): void
    {
        $required = [
            'customerName' => '用户名',
            'customerPhone' => '电话',
            'customerType' => '用户类型',
            'customerAddress' => '地址',
            'inspectorName' => '检查人',
            'deliveryPerson' => '配送员',
            'inspectionDate' => '检查日期',
            'checkResult' => '检查结果',
        ];

        $errors = [];
        foreach ($required as $key => $label) {
            if (trim((string) ($payload[$key] ?? '')) === '') {
                $errors[$key] = "{$label}不能为空";
            }
        }

        if (!in_array($payload['customerType'] ?? '', HazardCatalog::customerTypes(), true)) {
            $errors['customerType'] = '用户类型不正确';
        }

        $validResults = [
            HazardCatalog::RESULT_CLEARED,
            HazardCatalog::RESULT_NOT_CLEARED,
            HazardCatalog::RESULT_RECTIFIED,
        ];
        if (!in_array($payload['checkResult'] ?? '', $validResults, true)) {
            $errors['checkResult'] = '检查结果不正确';
        }

        $hazards = $payload['hazardItems'] ?? [];
        $gasSourceIssues = $payload['gasSourceIssues'] ?? [];
        if (($payload['checkResult'] ?? '') === HazardCatalog::RESULT_NOT_CLEARED
            && count($hazards) === 0
            && count($gasSourceIssues) === 0) {
            $errors['hazardItems'] = '隐患未清零时必须选择至少一个隐患项目或气源排查异常';
        }

        if ($errors) {
            throw new HttpException(422, '提交内容不完整，请补充后再提交。', $errors);
        }
    }

    /** @param array<string, mixed> $payload */
    private function validateDraft(array $payload): void
    {
        $required = [
            'clientUuid' => '草稿标识',
            'customerName' => '用户名',
            'customerPhone' => '电话',
            'customerType' => '用户类型',
            'customerAddress' => '地址',
            'inspectorName' => '检查人',
            'deliveryPerson' => '配送员',
            'inspectionDate' => '检查日期',
        ];

        $errors = [];
        foreach ($required as $key => $label) {
            if (trim((string) ($payload[$key] ?? '')) === '') {
                $errors[$key] = "{$label}不能为空";
            }
        }
        if (!in_array($payload['customerType'] ?? '', HazardCatalog::customerTypes(), true)) {
            $errors['customerType'] = '用户类型不正确';
        }
        if ($errors) {
            throw new HttpException(422, '草稿内容不完整，请先完成用户与现场信息。', $errors);
        }
    }

    /** @param array<string, mixed> $payload */
    private function validateCloudSubmission(array $payload): void
    {
        $errors = [];
        $photos = is_array($payload['photos'] ?? null) ? $payload['photos'] : [];
        if (count((array) ($photos['storefront'] ?? [])) === 0) {
            $errors['storefrontPhoto'] = '请拍摄并上传门头照片';
        }

        if (($payload['checkResult'] ?? '') === HazardCatalog::RESULT_NOT_CLEARED) {
            $hazardPhotos = is_array($photos['hazard'] ?? null) ? $photos['hazard'] : [];
            foreach ((array) ($payload['hazardItems'] ?? []) as $code) {
                if (count((array) ($hazardPhotos[$code] ?? [])) === 0) {
                    $errors["hazardPhoto.{$code}"] = '每个未清零隐患项目都必须上传照片';
                }
            }
        }

        if ($errors) {
            throw new HttpException(422, '照片上传不完整，不能提交正式记录。', $errors);
        }
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload @return array<string, mixed> */
    private function withOrganizationSelection(array $user, array $payload, bool $required = false): array
    {
        $companyId = (int) ($user['company_id'] ?? 0);
        if ($companyId <= 0) {
            throw new HttpException(422, '当前账号未分配公司，无法保存检查记录。');
        }

        $regionId = $this->positiveIdOrNull($payload['regionId'] ?? null);
        $storeId = $this->positiveIdOrNull($payload['storeId'] ?? null);
        if ($required && ($regionId === null || $storeId === null)) {
            $errors = [];
            if ($regionId === null) $errors['regionId'] = '请选择检查片区';
            if ($storeId === null) $errors['storeId'] = '请选择检查门店';
            throw new HttpException(422, '请选择本次检查的片区和门店后再提交。', $errors);
        }

        if ($storeId !== null) {
            $store = $this->organization->findStore($storeId);
            if (!$store
                || (int) $store['company_id'] !== $companyId
                || ($store['status'] ?? '') !== 'active'
                || ($store['region_status'] ?? '') !== 'active') {
                throw new HttpException(422, '所选门店不可用，请重新选择。', ['storeId' => '门店不存在、已停用或不属于当前公司']);
            }
            $storeRegionId = (int) $store['region_id'];
            if ($regionId !== null && $regionId !== $storeRegionId) {
                throw new HttpException(422, '门店与片区不匹配，请重新选择。', ['storeId' => '门店不属于所选片区']);
            }
            $regionId = $storeRegionId;
        }

        if ($regionId !== null) {
            $region = $this->organization->findRegion($regionId);
            if (!$region
                || (int) $region['company_id'] !== $companyId
                || ($region['status'] ?? '') !== 'active') {
                throw new HttpException(422, '所选片区不可用，请重新选择。', ['regionId' => '片区不存在、已停用或不属于当前公司']);
            }
        }

        $payload['regionId'] = $regionId;
        $payload['storeId'] = $storeId;
        return $payload;
    }

    private function positiveIdOrNull(mixed $value): ?int
    {
        $id = (int) ($value ?? 0);
        return $id > 0 ? $id : null;
    }
}

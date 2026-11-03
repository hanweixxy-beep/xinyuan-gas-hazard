<?php

namespace App\Repositories;

use App\Core\HttpException;
use App\Domain\HazardCatalog;
use App\Domain\UserAccess;
use App\Domain\RecordFilters;
use PDO;

final class RecordRepository
{
    public function __construct(private PDO $pdo)
    {
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $filters @return list<array<string, mixed>> */
    public function listForUser(array $user, array $filters = [], int $limit = 1000, int $offset = 0): array
    {
        [$where, $params] = $this->listConditions($user, $filters);
        $sql = "SELECT r.*, c.name AS company_name, rg.name AS region_name, st.name AS store_name,
                       u.name AS creator_name
                FROM inspection_records r
                INNER JOIN companies c ON c.id = r.company_id
                LEFT JOIN regions rg ON rg.id = r.region_id
                LEFT JOIN stores st ON st.id = r.store_id
                INNER JOIN users u ON u.id = r.creator_id
                WHERE {$where}
                ORDER BY r.inspection_date DESC, r.id DESC
                LIMIT :limit OFFSET :offset";
        $stmt = $this->pdo->prepare($sql);
        foreach ($params as $key => $value) {
            $stmt->bindValue($key, $value, is_int($value) ? PDO::PARAM_INT : PDO::PARAM_STR);
        }
        $stmt->bindValue('limit', $limit, PDO::PARAM_INT);
        $stmt->bindValue('offset', $offset, PDO::PARAM_INT);
        $stmt->execute();
        return $stmt->fetchAll();
    }

    /** Full filtered statistics and a bounded page; legacy clients remain compatible. */
    public function pageForUser(array $user, array $filters, int $page, int $pageSize): array
    {
        $pageSize = max(1, min(100, $pageSize));
        $page = max(1, $page);
        $this->pdo->beginTransaction();
        try {
            [$where, $params] = $this->listConditions($user, $filters);
            $from = "FROM inspection_records r INNER JOIN companies c ON c.id = r.company_id
                INNER JOIN users u ON u.id = r.creator_id WHERE {$where}";
            $stmt = $this->pdo->prepare("SELECT COUNT(*) AS total,
                COALESCE(SUM(r.status = 'pending'), 0) AS pending {$from}");
            $stmt->execute($params);
            $counts = $stmt->fetch();
            $total = (int) $counts['total'];
            $pending = (int) $counts['pending'];
            $pageCount = max(1, (int) ceil($total / $pageSize));
            $page = min($page, $pageCount);
            $records = $this->listForUser($user, $filters, $pageSize, ($page - 1) * $pageSize);
            $stmt = $this->pdo->prepare("SELECT r.inspection_date AS date, COUNT(*) AS count {$from}
                GROUP BY r.inspection_date ORDER BY r.inspection_date DESC LIMIT 7");
            $stmt->execute($params);
            $trend = array_reverse(array_map(static fn ($row) => ['date' => $row['date'], 'count' => (int) $row['count']], $stmt->fetchAll()));
            $pendingRecords = $pending > 0
                ? $this->listForUser($user, array_replace($filters, ['status' => 'pending']), 5) : [];
            $this->pdo->commit();
            return ['records' => $records, 'pagination' => ['page' => $page, 'pageSize' => $pageSize,
                'total' => $total, 'pageCount' => $pageCount], 'summary' => ['total' => $total,
                'pending' => $pending, 'done' => $total - $pending, 'trend' => $trend, 'pendingRecords' => $pendingRecords]];
        } catch (\Throwable $error) {
            $this->pdo->rollBack();
            throw $error;
        }
    }

    /** Stream an ordered ID snapshot inside the caller's repeatable-read transaction. */
    public function exportIds(array $user, array $filters): \Generator
    {
        [$where, $params] = $this->listConditions($user, $filters);
        $lastDate = '9999-12-31';
        $lastId = PHP_INT_MAX;
        do {
            $stmt = $this->pdo->prepare("SELECT r.id, r.inspection_date FROM inspection_records r
                WHERE {$where} AND (r.inspection_date < :page_date OR
                    (r.inspection_date = :page_equal_date AND r.id < :page_id))
                ORDER BY r.inspection_date DESC, r.id DESC LIMIT 1000");
            $stmt->execute($params + ['page_date' => $lastDate, 'page_equal_date' => $lastDate, 'page_id' => $lastId]);
            $page = $stmt->fetchAll();
            foreach ($page as $row) {
                $lastDate = $row['inspection_date'];
                $lastId = (int) $row['id'];
                yield $lastId;
            }
        } while (count($page) === 1000);
    }

    /** Fetch a small snapshot batch with four queries, preserving snapshot order. */
    public function exportBatch(array $user, array $ids): array
    {
        if ($ids === []) return [];
        $ids = array_map('intval', $ids);
        [$where, $params] = $this->scopeCondition($user, 'r');
        $in = implode(',', $ids);
        $stmt = $this->pdo->prepare("SELECT r.*, c.name AS company_name, rg.name AS region_name, st.name AS store_name
            FROM inspection_records r JOIN companies c ON c.id = r.company_id
            LEFT JOIN regions rg ON rg.id = r.region_id LEFT JOIN stores st ON st.id = r.store_id
            WHERE r.id IN ({$in}) AND r.record_state = 'submitted' AND {$where}");
        $stmt->execute($params);
        $rows = [];
        foreach ($stmt->fetchAll() as $row) {
            $rows[(int) $row['id']] = $row + ['hazards' => [], 'gasSourceIssues' => [], 'photos' => []];
        }
        foreach (['record_hazards' => 'hazards', 'record_gas_source_issues' => 'gasSourceIssues', 'record_photos' => 'photos'] as $table => $key) {
            foreach ($this->pdo->query("SELECT * FROM {$table} WHERE record_id IN ({$in}) ORDER BY id")->fetchAll() as $child) {
                if (isset($rows[(int) $child['record_id']])) $rows[(int) $child['record_id']][$key][] = $child;
            }
        }
        return array_values(array_filter(array_map(static fn (int $id): ?array => $rows[$id] ?? null, $ids)));
    }

    /** @param array<string, mixed> $user @return array<string, mixed>|null */
    public function findForUser(int $id, array $user): ?array
    {
        [$where, $params] = $this->scopeCondition($user, 'r');
        $params['id'] = $id;
        $stmt = $this->pdo->prepare(
            "SELECT r.*, c.name AS company_name, rg.name AS region_name, st.name AS store_name,
                    u.name AS creator_name
             FROM inspection_records r
             INNER JOIN companies c ON c.id = r.company_id
             LEFT JOIN regions rg ON rg.id = r.region_id
             LEFT JOIN stores st ON st.id = r.store_id
             INNER JOIN users u ON u.id = r.creator_id
             WHERE r.id = :id AND r.record_state = 'submitted' AND {$where}
             LIMIT 1"
        );
        $stmt->execute($params);
        $record = $stmt->fetch();
        if (!$record) {
            return null;
        }

        $record['hazards'] = $this->children('record_hazards', $id);
        $record['gasSourceIssues'] = $this->children('record_gas_source_issues', $id);
        $record['photos'] = $this->children('record_photos', $id);
        return $record;
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    public function create(array $user, array $payload): int
    {
        $hazardMap = HazardCatalog::hazardMap();
        $companyId = $this->resolveCompanyId($user, $payload);
        $status = match ($payload['checkResult']) {
            HazardCatalog::RESULT_NOT_CLEARED => 'pending',
            HazardCatalog::RESULT_RECTIFIED => 'rectified',
            default => 'done',
        };
        $clientUuid = $this->normalizeClientUuid($payload['clientUuid'] ?? $payload['uuid'] ?? null);

        $this->pdo->beginTransaction();
        try {
            if ($clientUuid !== null) {
                $existingId = $this->findExistingClientUuid($clientUuid, $user);
                if ($existingId !== null) {
                    $this->pdo->commit();
                    return $existingId;
                }
            }

            $this->lockInspectionStore($payload);
            $stmt = $this->pdo->prepare(
                "INSERT INTO inspection_records
                 (client_uuid, company_id, region_id, store_id, creator_id, record_state, customer_name, customer_phone, customer_type, customer_address,
                  inspector_name, delivery_person, inspection_date, check_result, status, remark,
                  location_lat, location_lng, location_address, location_accuracy_meters, location_captured_at,
                  rectification_date, rectification_note, rectification_person)
                 VALUES
                 (:client_uuid, :company_id, :region_id, :store_id, :creator_id, 'submitted', :customer_name, :customer_phone, :customer_type, :customer_address,
                  :inspector_name, :delivery_person, :inspection_date, :check_result, :status, :remark,
                  :location_lat, :location_lng, :location_address, :location_accuracy_meters, :location_captured_at,
                  :rectification_date, :rectification_note, :rectification_person)"
            );
            $stmt->execute([
                'client_uuid' => $clientUuid,
                'company_id' => $companyId,
                'region_id' => $this->resolveOrganizationId($payload, 'regionId'),
                'store_id' => $this->resolveOrganizationId($payload, 'storeId'),
                'creator_id' => (int) $user['id'],
                'customer_name' => $payload['customerName'],
                'customer_phone' => $payload['customerPhone'],
                'customer_type' => $payload['customerType'],
                'customer_address' => $payload['customerAddress'],
                'inspector_name' => $payload['inspectorName'],
                'delivery_person' => $payload['deliveryPerson'],
                'inspection_date' => $payload['inspectionDate'],
                'check_result' => $payload['checkResult'],
                'status' => $status,
                'remark' => $payload['remark'] ?? '',
                'location_lat' => $payload['location']['lat'] ?? null,
                'location_lng' => $payload['location']['lng'] ?? null,
                'location_address' => $payload['location']['address'] ?? $payload['customerAddress'],
                'location_accuracy_meters' => $payload['location']['accuracyMeters'] ?? $payload['location']['accuracy'] ?? null,
                'location_captured_at' => $payload['location']['capturedAt'] ?? null,
                'rectification_date' => $this->normalizeDate($payload['rectificationDate'] ?? null),
                'rectification_note' => $payload['rectificationNote'] ?? '',
                'rectification_person' => $payload['rectificationPerson'] ?? '',
            ]);
            $recordId = (int) $this->pdo->lastInsertId();

            foreach ($payload['hazardItems'] ?? [] as $code) {
                $item = $hazardMap[$code] ?? ['code' => $code, 'category' => '其他隐患', 'name' => $code];
                $this->insertHazard($recordId, $item);
            }

            foreach ($payload['gasSourceIssues'] ?? [] as $issue) {
                $this->insertGasSourceIssue($recordId, (string) $issue);
            }

            foreach ($this->flattenPhotos($payload['photos'] ?? []) as $photo) {
                $this->insertPhoto($recordId, $photo);
            }

            $this->pdo->commit();
            return $recordId;
        } catch (\Throwable $exception) {
            $this->pdo->rollBack();
            throw $exception;
        }
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    public function saveDraft(array $user, array $payload): int
    {
        $clientUuid = $this->normalizeClientUuid($payload['clientUuid'] ?? $payload['uuid'] ?? null);
        if ($clientUuid === null) {
            throw new HttpException(422, '云端草稿缺少客户端 UUID。');
        }

        $hazardMap = HazardCatalog::hazardMap();
        $status = match ($payload['checkResult'] ?? HazardCatalog::RESULT_CLEARED) {
            HazardCatalog::RESULT_NOT_CLEARED => 'pending',
            HazardCatalog::RESULT_RECTIFIED => 'rectified',
            default => 'done',
        };

        $this->pdo->beginTransaction();
        try {
            $existing = $this->findByClientUuid($clientUuid);
            if ($existing && (int) $existing['creator_id'] !== (int) $user['id']) {
                throw new HttpException(409, '该草稿标识已被其他账号使用。');
            }
            if ($existing && $existing['record_state'] === 'submitted') {
                $this->pdo->commit();
                return (int) $existing['id'];
            }

            $this->lockInspectionStore($payload);
            $values = [
                'client_uuid' => $clientUuid,
                'company_id' => $this->resolveCompanyId($user, $payload),
                'region_id' => $this->resolveOrganizationId($payload, 'regionId'),
                'store_id' => $this->resolveOrganizationId($payload, 'storeId'),
                'creator_id' => (int) $user['id'],
                'customer_name' => $payload['customerName'],
                'customer_phone' => $payload['customerPhone'],
                'customer_type' => $payload['customerType'],
                'customer_address' => $payload['customerAddress'],
                'inspector_name' => $payload['inspectorName'],
                'delivery_person' => $payload['deliveryPerson'],
                'inspection_date' => $payload['inspectionDate'],
                'check_result' => $payload['checkResult'] ?? HazardCatalog::RESULT_CLEARED,
                'status' => $status,
                'remark' => $payload['remark'] ?? '',
                'location_lat' => $payload['location']['lat'] ?? null,
                'location_lng' => $payload['location']['lng'] ?? null,
                'location_address' => $payload['location']['address'] ?? $payload['customerAddress'],
                'location_accuracy_meters' => $payload['location']['accuracyMeters'] ?? $payload['location']['accuracy'] ?? null,
                'location_captured_at' => $payload['location']['capturedAt'] ?? null,
                'rectification_date' => $this->normalizeDate($payload['rectificationDate'] ?? null),
                'rectification_note' => $payload['rectificationNote'] ?? '',
                'rectification_person' => $payload['rectificationPerson'] ?? '',
            ];

            if ($existing) {
                $recordId = (int) $existing['id'];
                $values['id'] = $recordId;
                $stmt = $this->pdo->prepare(
                    "UPDATE inspection_records
                     SET company_id = :company_id,
                         region_id = :region_id,
                         store_id = :store_id,
                         customer_name = :customer_name,
                         customer_phone = :customer_phone,
                         customer_type = :customer_type,
                         customer_address = :customer_address,
                         inspector_name = :inspector_name,
                         delivery_person = :delivery_person,
                         inspection_date = :inspection_date,
                         check_result = :check_result,
                         status = :status,
                         remark = :remark,
                         location_lat = :location_lat,
                         location_lng = :location_lng,
                         location_address = :location_address,
                         location_accuracy_meters = :location_accuracy_meters,
                         location_captured_at = :location_captured_at,
                         rectification_date = :rectification_date,
                         rectification_note = :rectification_note,
                         rectification_person = :rectification_person
                     WHERE id = :id"
                );
                unset($values['client_uuid'], $values['creator_id']);
                $stmt->execute($values);
            } else {
                $stmt = $this->pdo->prepare(
                    "INSERT INTO inspection_records
                     (client_uuid, company_id, region_id, store_id, creator_id, record_state, customer_name, customer_phone, customer_type, customer_address,
                      inspector_name, delivery_person, inspection_date, check_result, status, remark,
                      location_lat, location_lng, location_address, location_accuracy_meters, location_captured_at,
                      rectification_date, rectification_note, rectification_person)
                     VALUES
                     (:client_uuid, :company_id, :region_id, :store_id, :creator_id, 'draft', :customer_name, :customer_phone, :customer_type, :customer_address,
                      :inspector_name, :delivery_person, :inspection_date, :check_result, :status, :remark,
                      :location_lat, :location_lng, :location_address, :location_accuracy_meters, :location_captured_at,
                      :rectification_date, :rectification_note, :rectification_person)"
                );
                $stmt->execute($values);
                $recordId = (int) $this->pdo->lastInsertId();
            }

            $this->replaceDraftChildren($recordId, $payload, $hazardMap);
            $this->pdo->commit();
            return $recordId;
        } catch (\Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }
    }

    /** @param array<string, mixed> $user */
    public function submitDraftByClientUuid(string $clientUuid, array $user): int
    {
        $existing = $this->findByClientUuid($clientUuid);
        if (!$existing || (int) $existing['creator_id'] !== (int) $user['id']) {
            throw new HttpException(404, '云端草稿不存在，或你没有权限提交。');
        }

        $recordId = (int) $existing['id'];
        if ($existing['record_state'] === 'submitted') {
            return $recordId;
        }

        $stmt = $this->pdo->prepare(
            "UPDATE inspection_records
             SET record_state = 'submitted', updated_at = NOW()
             WHERE id = :id AND creator_id = :creator_id"
        );
        $stmt->execute([
            'id' => $recordId,
            'creator_id' => (int) $user['id'],
        ]);
        return $recordId;
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    public function rectify(int $id, array $user, array $payload): void
    {
        $record = $this->findForUser($id, $user);
        if (!$record) {
            return;
        }
        if (($user['role'] ?? '') === 'inspector' && (int) $record['creator_id'] !== (int) $user['id']) {
            throw new HttpException(403, '公司数据查看权限不包含修改或整改他人记录。');
        }

        $stmt = $this->pdo->prepare(
            "UPDATE inspection_records
             SET status = 'rectified',
                 check_result = :check_result,
                 rectification_date = :rectification_date,
                 rectification_note = :rectification_note,
                 rectification_person = :rectification_person
             WHERE id = :id"
        );
        $stmt->execute([
            'id' => $id,
            'check_result' => HazardCatalog::RESULT_RECTIFIED,
            'rectification_date' => $payload['rectificationDate'],
            'rectification_note' => $payload['rectificationNote'] ?? '',
            'rectification_person' => $payload['rectificationPerson'] ?? $user['name'] ?? '',
        ]);

        foreach ($payload['photos'] ?? [] as $filePath) {
            $this->insertPhoto($id, [
                'type' => 'rectification',
                'filePath' => (string) $filePath,
            ]);
        }
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $filters @return array{0:string,1:array<string,mixed>} */
    private function listConditions(array $user, array $filters): array
    {
        [$scopeWhere, $params] = $this->scopeCondition($user, 'r');
        $conditions = [$scopeWhere, "r.record_state = 'submitted'"];

        $status = (string) ($filters['status'] ?? '');
        if (in_array($status, ['done', 'pending', 'rectified'], true)) {
            $conditions[] = 'r.status = :status';
            $params['status'] = $status;
        }

        $dateFrom = (string) ($filters['dateFrom'] ?? '');
        if (preg_match('/^\d{4}-\d{2}-\d{2}$/', $dateFrom)) {
            $conditions[] = 'r.inspection_date >= :date_from';
            $params['date_from'] = $dateFrom;
        }

        $dateTo = (string) ($filters['dateTo'] ?? '');
        if (preg_match('/^\d{4}-\d{2}-\d{2}$/', $dateTo)) {
            $conditions[] = 'r.inspection_date <= :date_to';
            $params['date_to'] = $dateTo;
        }

        $keyword = trim((string) ($filters['keyword'] ?? ''));
        if ($keyword !== '') {
            $keywordColumns = [
                'r.customer_name',
                'r.customer_phone',
                'r.customer_address',
                'r.inspector_name',
                'r.delivery_person',
            ];
            $keywordConditions = [];
            foreach ($keywordColumns as $index => $column) {
                $param = "keyword_{$index}";
                $keywordConditions[] = "{$column} LIKE :{$param}";
                $params[$param] = '%' . $keyword . '%';
            }
            $conditions[] = '(' . implode(' OR ', $keywordConditions) . ')';
        }

        $companyId = (int) ($filters['companyId'] ?? 0);
        if ($companyId > 0 && $user['role'] === 'hq_admin') {
            $conditions[] = 'r.company_id = :filter_company_id';
            $params['filter_company_id'] = $companyId;
        }

        $regionId = (int) ($filters['regionId'] ?? 0);
        if ($regionId > 0) {
            $conditions[] = 'r.region_id = :filter_region_id';
            $params['filter_region_id'] = $regionId;
        }

        $storeId = (int) ($filters['storeId'] ?? 0);
        if ($storeId > 0) {
            $conditions[] = 'r.store_id = :filter_store_id';
            $params['filter_store_id'] = $storeId;
        }

        $textFilters = [
            'inspectorName' => ['column' => 'r.inspector_name', 'param' => 'filter_inspector_name'],
            'deliveryPerson' => ['column' => 'r.delivery_person', 'param' => 'filter_delivery_person'],
            'customerName' => ['column' => 'r.customer_name', 'param' => 'filter_customer_name'],
            'customerPhone' => ['column' => 'r.customer_phone', 'param' => 'filter_customer_phone'],
            'customerAddress' => ['column' => 'r.customer_address', 'param' => 'filter_customer_address'],
        ];
        foreach ($textFilters as $filterKey => $config) {
            $value = trim((string) ($filters[$filterKey] ?? ''));
            if ($value === '') {
                continue;
            }
            $conditions[] = "{$config['column']} LIKE :{$config['param']}";
            $params[$config['param']] = '%' . $value . '%';
        }

        $customerType = trim((string) ($filters['customerType'] ?? ''));
        if ($customerType !== '' && in_array($customerType, HazardCatalog::customerTypes(), true)) {
            $conditions[] = 'r.customer_type = :filter_customer_type';
            $params['filter_customer_type'] = $customerType;
        }

        $checkResult = trim((string) ($filters['checkResult'] ?? ''));
        if (in_array($checkResult, [
            HazardCatalog::RESULT_CLEARED,
            HazardCatalog::RESULT_NOT_CLEARED,
            HazardCatalog::RESULT_RECTIFIED,
        ], true)) {
            $conditions[] = 'r.check_result = :filter_check_result';
            $params['filter_check_result'] = $checkResult;
        }

        $hazardCodes = RecordFilters::selections($filters['hazardCodes'] ?? []);
        $legacyCode = trim((string) ($filters['hazardCode'] ?? ''));
        if ($legacyCode !== '') $hazardCodes[] = $legacyCode;
        $gasIssues = RecordFilters::selections($filters['gasSourceIssues'] ?? []);
        foreach ([
            ['items' => array_unique($hazardCodes), 'table' => 'record_hazards', 'column' => 'hazard_code', 'prefix' => 'hazard'],
            ['items' => $gasIssues, 'table' => 'record_gas_source_issues', 'column' => 'issue_text', 'prefix' => 'gas'],
        ] as $selection) {
            if (!$selection['items']) continue;
            $placeholders = [];
            foreach (array_values($selection['items']) as $index => $value) {
                $key = 'filter_' . $selection['prefix'] . '_' . $index;
                $placeholders[] = ':' . $key;
                $params[$key] = $value;
            }
            $conditions[] = 'EXISTS (SELECT 1 FROM ' . $selection['table'] . ' f
                WHERE f.record_id = r.id AND f.' . $selection['column'] . ' IN (' . implode(',', $placeholders) . '))';
        }

        return [implode(' AND ', $conditions), $params];
    }

    /** @param array<string, mixed> $user @return array{0:string,1:array<string,mixed>} */
    private function scopeCondition(array $user, string $alias): array
    {
        $scope = UserAccess::recordScope($user);
        if ($scope === 'all') {
            return ['1 = 1', []];
        }
        if ($scope === 'company') {
            return [
                "{$alias}.company_id = :scope_company_id",
                ['scope_company_id' => (int) $user['company_id']],
            ];
        }
        return ["{$alias}.creator_id = :creator_id", ['creator_id' => (int) $user['id']]];
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    private function resolveCompanyId(array $user, array $payload): int
    {
        return (int) $user['company_id'];
    }

    /** @param array<string, mixed> $payload */
    private function lockInspectionStore(array $payload): void
    {
        $storeId = $this->resolveOrganizationId($payload, 'storeId');
        if ($storeId === null) return;
        $stmt = $this->pdo->prepare('SELECT region_id FROM stores WHERE id = :id FOR SHARE');
        $stmt->execute(['id' => $storeId]);
        $regionId = $stmt->fetchColumn();
        if ($regionId === false || (int) $regionId !== $this->resolveOrganizationId($payload, 'regionId')) {
            throw new HttpException(409, '门店所属片区已变更，请重新选择片区和门店。');
        }
    }

    /** @param array<string, mixed> $payload */
    private function resolveOrganizationId(array $payload, string $payloadKey): ?int
    {
        $value = $payload[$payloadKey] ?? null;
        $id = (int) ($value ?? 0);
        return $id > 0 ? $id : null;
    }

    private function normalizeClientUuid(mixed $value): ?string
    {
        $uuid = trim((string) ($value ?? ''));
        return $uuid === '' ? null : substr($uuid, 0, 80);
    }

    private function normalizeDate(mixed $value): ?string
    {
        $date = trim((string) ($value ?? ''));
        return preg_match('/^\d{4}-\d{2}-\d{2}$/', $date) ? $date : null;
    }

    /** @param array<string, mixed> $user */
    private function findExistingClientUuid(string $clientUuid, array $user): ?int
    {
        [$where, $params] = $this->scopeCondition($user, 'r');
        $params['client_uuid'] = $clientUuid;
        $stmt = $this->pdo->prepare(
            "SELECT r.id FROM inspection_records r
             WHERE r.client_uuid = :client_uuid AND {$where}
             LIMIT 1"
        );
        $stmt->execute($params);
        $id = $stmt->fetchColumn();
        return $id === false ? null : (int) $id;
    }

    /** @return array<string, mixed>|null */
    private function findByClientUuid(string $clientUuid): ?array
    {
        $stmt = $this->pdo->prepare(
            'SELECT id, creator_id, record_state FROM inspection_records WHERE client_uuid = :client_uuid LIMIT 1'
        );
        $stmt->execute(['client_uuid' => $clientUuid]);
        $record = $stmt->fetch();
        return $record ?: null;
    }

    /** @param array<string, mixed> $payload @param array<string, array{code:string,category:string,name:string}> $hazardMap */
    private function replaceDraftChildren(int $recordId, array $payload, array $hazardMap): void
    {
        foreach (['record_hazards', 'record_gas_source_issues', 'record_photos'] as $table) {
            $stmt = $this->pdo->prepare("DELETE FROM {$table} WHERE record_id = :record_id");
            $stmt->execute(['record_id' => $recordId]);
        }

        foreach ($payload['hazardItems'] ?? [] as $code) {
            $item = $hazardMap[$code] ?? ['code' => $code, 'category' => '其他隐患', 'name' => $code];
            $this->insertHazard($recordId, $item);
        }
        foreach ($payload['gasSourceIssues'] ?? [] as $issue) {
            $this->insertGasSourceIssue($recordId, (string) $issue);
        }
        foreach ($this->flattenPhotos($payload['photos'] ?? []) as $photo) {
            $this->insertPhoto($recordId, $photo);
        }
    }

    /** @return list<array<string, mixed>> */
    private function children(string $table, int $recordId): array
    {
        $stmt = $this->pdo->prepare("SELECT * FROM {$table} WHERE record_id = :record_id ORDER BY id ASC");
        $stmt->execute(['record_id' => $recordId]);
        return $stmt->fetchAll();
    }

    /** @param array{code:string, category:string, name:string} $item */
    private function insertHazard(int $recordId, array $item): void
    {
        $stmt = $this->pdo->prepare(
            "INSERT INTO record_hazards (record_id, hazard_code, hazard_category, hazard_name)
             VALUES (:record_id, :hazard_code, :hazard_category, :hazard_name)"
        );
        $stmt->execute([
            'record_id' => $recordId,
            'hazard_code' => $item['code'],
            'hazard_category' => $item['category'],
            'hazard_name' => $item['name'],
        ]);
    }

    private function insertGasSourceIssue(int $recordId, string $issue): void
    {
        if (trim($issue) === '') {
            return;
        }
        $stmt = $this->pdo->prepare(
            "INSERT INTO record_gas_source_issues (record_id, issue_text)
             VALUES (:record_id, :issue_text)"
        );
        $stmt->execute(['record_id' => $recordId, 'issue_text' => $issue]);
    }

    /** @param array<string, mixed> $photo */
    private function insertPhoto(int $recordId, array $photo): void
    {
        if (empty($photo['filePath'])) {
            return;
        }
        $stmt = $this->pdo->prepare(
            "INSERT INTO record_photos
             (record_id, photo_type, hazard_code, file_path, original_name, mime_type, file_size,
              watermark_address, latitude, longitude, accuracy_meters, captured_at)
             VALUES
             (:record_id, :photo_type, :hazard_code, :file_path, :original_name, :mime_type, :file_size,
              :watermark_address, :latitude, :longitude, :accuracy_meters, :captured_at)"
        );
        $stmt->execute([
            'record_id' => $recordId,
            'photo_type' => $photo['type'],
            'hazard_code' => $photo['hazardCode'] ?? null,
            'file_path' => $photo['filePath'],
            'original_name' => $photo['originalName'] ?? null,
            'mime_type' => $photo['mimeType'] ?? null,
            'file_size' => $photo['fileSize'] ?? null,
            'watermark_address' => $photo['watermarkAddress'] ?? null,
            'latitude' => $photo['latitude'] ?? null,
            'longitude' => $photo['longitude'] ?? null,
            'accuracy_meters' => $photo['accuracyMeters'] ?? null,
            'captured_at' => $photo['capturedAt'] ?? null,
        ]);
    }

    /** @param array<string, mixed> $photos @return list<array<string, mixed>> */
    private function flattenPhotos(array $photos): array
    {
        $flat = [];
        foreach (['storefront', 'cylinder_qr', 'cylinder', 'rectification'] as $type) {
            foreach (($photos[$type] ?? []) as $photo) {
                $normalized = $this->normalizePhoto($photo, $type);
                if ($normalized) {
                    $flat[] = $normalized;
                }
            }
        }
        foreach (($photos['hazard'] ?? []) as $code => $items) {
            foreach ((array) $items as $photo) {
                $normalized = $this->normalizePhoto($photo, 'hazard', (string) $code);
                if ($normalized) {
                    $flat[] = $normalized;
                }
            }
        }
        return $flat;
    }

    /** @return array<string, mixed>|null */
    private function normalizePhoto(mixed $photo, string $type, ?string $hazardCode = null): ?array
    {
        if (is_string($photo)) {
            $filePath = trim($photo);
            return $filePath === '' ? null : [
                'type' => $type,
                'hazardCode' => $hazardCode,
                'filePath' => $filePath,
            ];
        }
        if (!is_array($photo)) {
            return null;
        }

        $filePath = trim((string) ($photo['filePath'] ?? ''));
        if ($filePath === '') {
            return null;
        }
        return array_merge($photo, [
            'type' => $type,
            'hazardCode' => $hazardCode ?? ($photo['hazardCode'] ?? null),
            'filePath' => $filePath,
        ]);
    }
}

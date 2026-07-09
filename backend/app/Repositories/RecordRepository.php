<?php

namespace App\Repositories;

use App\Domain\HazardCatalog;
use PDO;

final class RecordRepository
{
    public function __construct(private PDO $pdo)
    {
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $filters @return list<array<string, mixed>> */
    public function listForUser(array $user, array $filters = [], int $limit = 1000): array
    {
        [$where, $params] = $this->listConditions($user, $filters);
        $sql = "SELECT r.*, c.name AS company_name, u.name AS creator_name
                FROM inspection_records r
                INNER JOIN companies c ON c.id = r.company_id
                INNER JOIN users u ON u.id = r.creator_id
                WHERE {$where}
                ORDER BY r.inspection_date DESC, r.id DESC
                LIMIT :limit";
        $stmt = $this->pdo->prepare($sql);
        foreach ($params as $key => $value) {
            $stmt->bindValue($key, $value, is_int($value) ? PDO::PARAM_INT : PDO::PARAM_STR);
        }
        $stmt->bindValue('limit', $limit, PDO::PARAM_INT);
        $stmt->execute();
        return $stmt->fetchAll();
    }

    /** @param array<string, mixed> $user @return array<string, mixed>|null */
    public function findForUser(int $id, array $user): ?array
    {
        [$where, $params] = $this->scopeCondition($user, 'r');
        $params['id'] = $id;
        $stmt = $this->pdo->prepare(
            "SELECT r.*, c.name AS company_name, u.name AS creator_name
             FROM inspection_records r
             INNER JOIN companies c ON c.id = r.company_id
             INNER JOIN users u ON u.id = r.creator_id
             WHERE r.id = :id AND {$where}
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

            $stmt = $this->pdo->prepare(
                "INSERT INTO inspection_records
                 (client_uuid, company_id, creator_id, customer_name, customer_phone, customer_type, customer_address,
                  inspector_name, delivery_person, inspection_date, check_result, status, remark,
                  location_lat, location_lng, location_address, rectification_date, rectification_note, rectification_person)
                 VALUES
                 (:client_uuid, :company_id, :creator_id, :customer_name, :customer_phone, :customer_type, :customer_address,
                  :inspector_name, :delivery_person, :inspection_date, :check_result, :status, :remark,
                  :location_lat, :location_lng, :location_address, :rectification_date, :rectification_note, :rectification_person)"
            );
            $stmt->execute([
                'client_uuid' => $clientUuid,
                'company_id' => $companyId,
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
    public function rectify(int $id, array $user, array $payload): void
    {
        $record = $this->findForUser($id, $user);
        if (!$record) {
            return;
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
        $conditions = [$scopeWhere];

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

        return [implode(' AND ', $conditions), $params];
    }

    /** @param array<string, mixed> $user @return array{0:string,1:array<string,mixed>} */
    private function scopeCondition(array $user, string $alias): array
    {
        if ($user['role'] === 'hq_admin') {
            return ['1 = 1', []];
        }
        if ($user['role'] === 'company_admin') {
            return ["{$alias}.company_id = :company_id", ['company_id' => (int) $user['company_id']]];
        }
        return ["{$alias}.creator_id = :creator_id", ['creator_id' => (int) $user['id']]];
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    private function resolveCompanyId(array $user, array $payload): int
    {
        if ($user['role'] === 'hq_admin' && !empty($payload['companyId'])) {
            return (int) $payload['companyId'];
        }
        return (int) $user['company_id'];
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
              watermark_address, latitude, longitude, captured_at)
             VALUES
             (:record_id, :photo_type, :hazard_code, :file_path, :original_name, :mime_type, :file_size,
              :watermark_address, :latitude, :longitude, :captured_at)"
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
            'captured_at' => $photo['capturedAt'] ?? null,
        ]);
    }

    /** @param array<string, mixed> $photos @return list<array<string, mixed>> */
    private function flattenPhotos(array $photos): array
    {
        $flat = [];
        foreach (['storefront', 'cylinder_qr', 'cylinder', 'rectification'] as $type) {
            foreach (($photos[$type] ?? []) as $filePath) {
                $flat[] = ['type' => $type, 'filePath' => (string) $filePath];
            }
        }
        foreach (($photos['hazard'] ?? []) as $code => $items) {
            foreach ((array) $items as $filePath) {
                $flat[] = ['type' => 'hazard', 'hazardCode' => (string) $code, 'filePath' => (string) $filePath];
            }
        }
        return $flat;
    }
}

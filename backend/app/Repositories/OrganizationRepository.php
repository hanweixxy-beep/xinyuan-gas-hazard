<?php

namespace App\Repositories;

use PDO;

final class OrganizationRepository
{
    public function __construct(private PDO $pdo)
    {
    }

    /** @return list<array<string, mixed>> */
    public function listRegions(?int $companyId = null): array
    {
        $sql = "SELECT r.id, r.company_id, c.name AS company_name, r.name, r.status, r.created_at, r.updated_at
                FROM regions r
                INNER JOIN companies c ON c.id = r.company_id";
        if ($companyId !== null) {
            $sql .= ' WHERE r.company_id = :company_id';
        }
        $sql .= ' ORDER BY c.id ASC, r.name ASC, r.id ASC';

        $stmt = $this->pdo->prepare($sql);
        $stmt->execute($companyId === null ? [] : ['company_id' => $companyId]);
        return $stmt->fetchAll();
    }

    /** @return list<array<string, mixed>> */
    public function listStores(?int $companyId = null): array
    {
        $sql = "SELECT s.id, s.company_id, c.name AS company_name, s.region_id, r.name AS region_name,
                       r.status AS region_status, s.name, s.address, s.status, s.created_at, s.updated_at
                FROM stores s
                INNER JOIN companies c ON c.id = s.company_id
                INNER JOIN regions r ON r.id = s.region_id";
        if ($companyId !== null) {
            $sql .= ' WHERE s.company_id = :company_id';
        }
        $sql .= ' ORDER BY c.id ASC, r.name ASC, s.name ASC, s.id ASC';

        $stmt = $this->pdo->prepare($sql);
        $stmt->execute($companyId === null ? [] : ['company_id' => $companyId]);
        return $stmt->fetchAll();
    }

    /** @return list<array<string, mixed>> */
    public function listActiveRegions(int $companyId): array
    {
        return array_values(array_filter(
            $this->listRegions($companyId),
            static fn (array $region): bool => ($region['status'] ?? '') === 'active'
        ));
    }

    /** @return list<array<string, mixed>> */
    public function listActiveStores(int $companyId): array
    {
        return array_values(array_filter(
            $this->listStores($companyId),
            static fn (array $store): bool => ($store['status'] ?? '') === 'active'
                && ($store['region_status'] ?? '') === 'active'
        ));
    }

    /** @return array<string, mixed>|null */
    public function findRegion(int $id): ?array
    {
        $stmt = $this->pdo->prepare(
            "SELECT r.*, c.name AS company_name
             FROM regions r
             INNER JOIN companies c ON c.id = r.company_id
             WHERE r.id = :id
             LIMIT 1"
        );
        $stmt->execute(['id' => $id]);
        $region = $stmt->fetch();
        return $region ?: null;
    }

    /** @return array<string, mixed>|null */
    public function findStore(int $id): ?array
    {
        $stmt = $this->pdo->prepare(
            "SELECT s.*, r.name AS region_name, r.status AS region_status
             FROM stores s
             INNER JOIN regions r ON r.id = s.region_id
             WHERE s.id = :id
             LIMIT 1"
        );
        $stmt->execute(['id' => $id]);
        $store = $stmt->fetch();
        return $store ?: null;
    }

    public function regionNameExists(int $companyId, string $name): bool
    {
        $stmt = $this->pdo->prepare(
            'SELECT 1 FROM regions WHERE company_id = :company_id AND name = :name LIMIT 1'
        );
        $stmt->execute(['company_id' => $companyId, 'name' => $name]);
        return (bool) $stmt->fetchColumn();
    }

    public function storeNameExists(int $regionId, string $name): bool
    {
        $stmt = $this->pdo->prepare(
            'SELECT 1 FROM stores WHERE region_id = :region_id AND name = :name LIMIT 1'
        );
        $stmt->execute(['region_id' => $regionId, 'name' => $name]);
        return (bool) $stmt->fetchColumn();
    }

    public function createRegion(int $companyId, string $name): int
    {
        $stmt = $this->pdo->prepare(
            "INSERT INTO regions (company_id, name, status) VALUES (:company_id, :name, 'active')"
        );
        $stmt->execute(['company_id' => $companyId, 'name' => $name]);
        return (int) $this->pdo->lastInsertId();
    }

    public function createStore(int $companyId, int $regionId, string $name, string $address): int
    {
        $stmt = $this->pdo->prepare(
            "INSERT INTO stores (company_id, region_id, name, address, status)
             VALUES (:company_id, :region_id, :name, :address, 'active')"
        );
        $stmt->execute([
            'company_id' => $companyId,
            'region_id' => $regionId,
            'name' => $name,
            'address' => $address,
        ]);
        return (int) $this->pdo->lastInsertId();
    }

    public function updateRegionStatus(int $id, string $status): void
    {
        $this->pdo->beginTransaction();
        try {
            $stmt = $this->pdo->prepare('UPDATE regions SET status = :status WHERE id = :id');
            $stmt->execute(['id' => $id, 'status' => $status]);
            if ($status === 'disabled') {
                $stores = $this->pdo->prepare("UPDATE stores SET status = 'disabled' WHERE region_id = :region_id");
                $stores->execute(['region_id' => $id]);
            }
            $this->pdo->commit();
        } catch (\Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }
    }

    public function updateStoreStatus(int $id, string $status): void
    {
        $stmt = $this->pdo->prepare('UPDATE stores SET status = :status WHERE id = :id');
        $stmt->execute(['id' => $id, 'status' => $status]);
    }
}

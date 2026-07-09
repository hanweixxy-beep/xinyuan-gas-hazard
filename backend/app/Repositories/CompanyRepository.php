<?php

namespace App\Repositories;

use PDO;

final class CompanyRepository
{
    public function __construct(private PDO $pdo)
    {
    }

    /** @return list<array<string, mixed>> */
    public function listAll(): array
    {
        $stmt = $this->pdo->query(
            "SELECT id, name, code, status, created_at, updated_at
             FROM companies
             ORDER BY id ASC"
        );
        return $stmt->fetchAll();
    }

    /** @return array<string, mixed>|null */
    public function find(int $id): ?array
    {
        $stmt = $this->pdo->prepare(
            "SELECT id, name, code, status, created_at, updated_at
             FROM companies
             WHERE id = :id
             LIMIT 1"
        );
        $stmt->execute(['id' => $id]);
        $company = $stmt->fetch();
        return $company ?: null;
    }

    /** @return array<string, mixed>|null */
    public function findByCode(string $code): ?array
    {
        $stmt = $this->pdo->prepare(
            "SELECT id, name, code, status, created_at, updated_at
             FROM companies
             WHERE code = :code
             LIMIT 1"
        );
        $stmt->execute(['code' => $code]);
        $company = $stmt->fetch();
        return $company ?: null;
    }

    public function create(string $name, string $code): int
    {
        $stmt = $this->pdo->prepare(
            "INSERT INTO companies (name, code, status)
             VALUES (:name, :code, 'active')"
        );
        $stmt->execute(['name' => $name, 'code' => $code]);
        return (int) $this->pdo->lastInsertId();
    }
}

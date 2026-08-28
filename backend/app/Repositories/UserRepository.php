<?php

namespace App\Repositories;

use PDO;

final class UserRepository
{
    public function __construct(private PDO $pdo)
    {
    }

    /** @return array<string, mixed>|null */
    public function findActiveByPhone(string $phone): ?array
    {
        $stmt = $this->pdo->prepare(
            "SELECT u.*, c.name AS company_name
             FROM users u
             LEFT JOIN companies c ON c.id = u.company_id
             WHERE u.phone = :phone AND u.status = 'active'
             LIMIT 1"
        );
        $stmt->execute(['phone' => $phone]);
        $user = $stmt->fetch();
        return $user ?: null;
    }

    /** @return array<string, mixed>|null */
    public function findByToken(string $token): ?array
    {
        $stmt = $this->pdo->prepare(
            "SELECT u.*, c.name AS company_name
             FROM sessions s
             INNER JOIN users u ON u.id = s.user_id
             LEFT JOIN companies c ON c.id = u.company_id
             WHERE s.token_hash = :token_hash
               AND s.expires_at > NOW()
               AND u.status = 'active'
             LIMIT 1"
        );
        $stmt->execute(['token_hash' => hash('sha256', $token)]);
        $user = $stmt->fetch();
        return $user ?: null;
    }

    public function createSession(int $userId, string $token, int $days, string $clientType): void
    {
        $expiresAt = (new \DateTimeImmutable())
            ->modify('+' . max(1, $days) . ' days')
            ->format('Y-m-d H:i:s');

        $stmt = $this->pdo->prepare(
            "INSERT INTO sessions (user_id, client_type, token_hash, expires_at)
             VALUES (:user_id, :client_type, :token_hash, :expires_at)
             ON DUPLICATE KEY UPDATE
               token_hash = VALUES(token_hash),
               expires_at = VALUES(expires_at),
               created_at = CURRENT_TIMESTAMP"
        );
        $stmt->bindValue('user_id', $userId, PDO::PARAM_INT);
        $stmt->bindValue('client_type', $clientType);
        $stmt->bindValue('token_hash', hash('sha256', $token));
        $stmt->bindValue('expires_at', $expiresAt);
        $stmt->execute();

        $update = $this->pdo->prepare('UPDATE users SET last_login_at = NOW() WHERE id = :id');
        $update->execute(['id' => $userId]);
    }

    /** @param array<string, mixed> $actor @return list<array<string, mixed>> */
    public function listForAdmin(array $actor): array
    {
        if ($actor['role'] === 'hq_admin') {
            $stmt = $this->pdo->query(
                "SELECT u.id, u.company_id, c.name AS company_name, u.name, u.phone, u.role, u.login_scope, u.can_view_company_records, u.status, u.last_login_at, u.created_at, u.updated_at
                 FROM users u
                 LEFT JOIN companies c ON c.id = u.company_id
                 ORDER BY u.id ASC"
            );
            return $stmt->fetchAll();
        }

        $stmt = $this->pdo->prepare(
            "SELECT u.id, u.company_id, c.name AS company_name, u.name, u.phone, u.role, u.login_scope, u.can_view_company_records, u.status, u.last_login_at, u.created_at, u.updated_at
             FROM users u
             LEFT JOIN companies c ON c.id = u.company_id
             WHERE u.company_id = :company_id
             ORDER BY u.id ASC"
        );
        $stmt->execute(['company_id' => (int) $actor['company_id']]);
        return $stmt->fetchAll();
    }

    /** @return array<string, mixed>|null */
    public function findById(int $id): ?array
    {
        $stmt = $this->pdo->prepare(
            "SELECT u.*, c.name AS company_name
             FROM users u
             LEFT JOIN companies c ON c.id = u.company_id
             WHERE u.id = :id
             LIMIT 1"
        );
        $stmt->execute(['id' => $id]);
        $user = $stmt->fetch();
        return $user ?: null;
    }

    /** @return array<string, mixed>|null */
    public function findByPhone(string $phone): ?array
    {
        $stmt = $this->pdo->prepare(
            "SELECT u.*, c.name AS company_name
             FROM users u
             LEFT JOIN companies c ON c.id = u.company_id
             WHERE u.phone = :phone
             LIMIT 1"
        );
        $stmt->execute(['phone' => $phone]);
        $user = $stmt->fetch();
        return $user ?: null;
    }

    /** @param array<string, mixed> $user */
    public function createUser(array $user): int
    {
        $stmt = $this->pdo->prepare(
            "INSERT INTO users (company_id, name, phone, password_hash, role, login_scope, can_view_company_records, status)
             VALUES (:company_id, :name, :phone, :password_hash, :role, :login_scope, :can_view_company_records, 'active')"
        );
        $stmt->execute([
            'company_id' => $user['company_id'],
            'name' => $user['name'],
            'phone' => $user['phone'],
            'password_hash' => $user['password_hash'],
            'role' => $user['role'],
            'login_scope' => $user['login_scope'],
            'can_view_company_records' => (int) ($user['can_view_company_records'] ?? 0),
        ]);
        return (int) $this->pdo->lastInsertId();
    }

    public function updateStatus(int $id, string $status): void
    {
        $stmt = $this->pdo->prepare('UPDATE users SET status = :status WHERE id = :id');
        $stmt->execute(['id' => $id, 'status' => $status]);
    }

    public function updatePassword(int $id, string $passwordHash): void
    {
        $stmt = $this->pdo->prepare('UPDATE users SET password_hash = :password_hash WHERE id = :id');
        $stmt->execute(['id' => $id, 'password_hash' => $passwordHash]);
    }

    public function updateAccess(int $id, string $loginScope, bool $canViewCompanyRecords): void
    {
        $stmt = $this->pdo->prepare(
            'UPDATE users
             SET login_scope = :login_scope, can_view_company_records = :can_view_company_records
             WHERE id = :id'
        );
        $stmt->execute([
            'id' => $id,
            'login_scope' => $loginScope,
            'can_view_company_records' => $canViewCompanyRecords ? 1 : 0,
        ]);
    }

    public function deleteSessionsForUser(int $id): void
    {
        $stmt = $this->pdo->prepare('DELETE FROM sessions WHERE user_id = :id');
        $stmt->execute(['id' => $id]);
    }
}

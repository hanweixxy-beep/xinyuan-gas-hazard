<?php

namespace App\Services;

use App\Core\Config;
use App\Core\HttpException;
use App\Repositories\UserRepository;

final class AuthService
{
    public function __construct(private UserRepository $users, private Config $config)
    {
    }

    /** @return array<string, mixed> */
    public function login(string $phone, string $password): array
    {
        $user = $this->users->findActiveByPhone($phone);
        if (!$user || !$this->verifyPassword($password, (string) $user['password_hash'])) {
            throw new HttpException(401, '手机号或密码不正确。');
        }

        $token = bin2hex(random_bytes(32));
        $this->users->createSession((int) $user['id'], $token, (int) $this->config->get('session_days', 7));

        return [
            'token' => $token,
            'user' => $this->publicUser($user),
        ];
    }

    /** @return array<string, mixed> */
    public function requireUser(?string $token): array
    {
        if (!$token) {
            throw new HttpException(401, '请先登录。');
        }

        $user = $this->users->findByToken($token);
        if (!$user) {
            throw new HttpException(401, '登录已过期，请重新登录。');
        }

        return $user;
    }

    public function changePassword(?string $token, string $currentPassword, string $newPassword): void
    {
        $user = $this->requireUser($token);
        if (!$this->verifyPassword($currentPassword, (string) $user['password_hash'])) {
            throw new HttpException(422, '当前密码不正确。');
        }
        if (mb_strlen($newPassword) < 8) {
            throw new HttpException(422, '新密码至少 8 位。');
        }

        $this->users->updatePassword((int) $user['id'], password_hash($newPassword, PASSWORD_DEFAULT));
        $this->users->deleteSessionsForUser((int) $user['id']);
    }

    /** @param array<string, mixed> $user @return array<string, mixed> */
    public function publicUser(array $user): array
    {
        return [
            'id' => (int) $user['id'],
            'companyId' => isset($user['company_id']) ? (int) $user['company_id'] : null,
            'companyName' => $user['company_name'] ?? '',
            'name' => $user['name'],
            'phone' => $user['phone'],
            'role' => $user['role'],
        ];
    }

    private function verifyPassword(string $password, string $stored): bool
    {
        if (str_starts_with($stored, 'sha256$')) {
            [, $salt, $hash] = explode('$', $stored, 3);
            return hash_equals($hash, hash('sha256', $salt . ':' . $password));
        }

        return password_verify($password, $stored);
    }
}

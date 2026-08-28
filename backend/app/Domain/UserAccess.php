<?php

namespace App\Domain;

final class UserAccess
{
    /** @return list<string> */
    public static function allowedLoginScopes(string $role): array
    {
        return $role === 'inspector'
            ? ['both', 'app', 'admin']
            : ['both', 'admin'];
    }

    public static function defaultLoginScope(string $role): string
    {
        return 'both';
    }

    /** @param array<string, mixed> $user */
    public static function recordScope(array $user): string
    {
        return match ((string) ($user['role'] ?? '')) {
            'hq_admin' => 'all',
            'company_admin' => 'company',
            'inspector' => !empty($user['can_view_company_records']) ? 'company' : 'own',
            default => 'own',
        };
    }

    /** @param array<string, mixed> $user */
    public static function canViewCompanyRecords(array $user): bool
    {
        return self::recordScope($user) === 'company';
    }
}

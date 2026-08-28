<?php

namespace App\Http\Controllers;

use App\Core\Request;
use App\Services\AdminService;
use App\Services\AuthService;

final class AdminController
{
    public function __construct(private AuthService $auth, private AdminService $admin)
    {
    }

    /** @return array<string, mixed> */
    public function companies(Request $request): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        return ['companies' => $this->admin->companies($user)];
    }

    /** @return array<string, mixed> */
    public function createCompany(Request $request): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        return ['id' => $this->admin->createCompany($user, $request->json())];
    }

    /** @return array<string, mixed> */
    public function users(Request $request): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        return ['users' => $this->admin->users($user)];
    }

    /** @return array<string, mixed> */
    public function createUser(Request $request): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        return ['id' => $this->admin->createUser($user, $request->json())];
    }

    /** @param array<string, string> $params @return array<string, mixed> */
    public function updateUserStatus(Request $request, array $params): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        $this->admin->updateUserStatus($user, (int) $params['id'], (string) $request->input('status', ''));
        return ['id' => (int) $params['id']];
    }

    /** @param array<string, string> $params @return array<string, mixed> */
    public function updateUserAccess(Request $request, array $params): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        $this->admin->updateUserAccess($user, (int) $params['id'], $request->json());
        return ['id' => (int) $params['id']];
    }

    /** @param array<string, string> $params @return array<string, mixed> */
    public function resetPassword(Request $request, array $params): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        $this->admin->resetPassword($user, (int) $params['id'], (string) $request->input('password', ''));
        return ['id' => (int) $params['id']];
    }
}

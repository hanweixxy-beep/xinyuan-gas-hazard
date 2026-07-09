<?php

namespace App\Http\Controllers;

use App\Core\Request;
use App\Services\AuthService;

final class AuthController
{
    public function __construct(private AuthService $auth)
    {
    }

    /** @return array<string, mixed> */
    public function login(Request $request): array
    {
        return $this->auth->login(
            trim((string) $request->input('phone')),
            (string) $request->input('password')
        );
    }

    /** @return array<string, mixed> */
    public function me(Request $request): array
    {
        return ['user' => $this->auth->publicUser($this->auth->requireUser($request->bearerToken()))];
    }

    /** @return array<string, mixed> */
    public function changePassword(Request $request): array
    {
        $this->auth->changePassword(
            $request->bearerToken(),
            (string) $request->input('currentPassword', ''),
            (string) $request->input('newPassword', '')
        );
        return ['changed' => true];
    }
}

<?php

namespace App\Http\Controllers;

use App\Core\Request;
use App\Services\AuthService;
use App\Services\FileStorageService;

final class FileController
{
    public function __construct(private AuthService $auth, private FileStorageService $storage)
    {
    }

    /** @return array<string, mixed> */
    public function upload(Request $request): array
    {
        $this->auth->requireUser($request->bearerToken());
        return $this->storage->storeUploadedFile();
    }
}


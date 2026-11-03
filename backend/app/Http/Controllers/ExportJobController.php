<?php

namespace App\Http\Controllers;

use App\Core\Request;
use App\Services\AuthService;
use App\Services\ExportJobService;

final class ExportJobController
{
    public function __construct(private AuthService $auth, private ExportJobService $jobs) {}

    public function index(Request $request): array
    {
        return ['jobs' => $this->jobs->list($this->auth->requireUser($request->bearerToken()))];
    }
    public function create(Request $request): array
    {
        return ['job' => $this->jobs->create($this->auth->requireUser($request->bearerToken()), $request->json())];
    }
    public function cancel(Request $request, array $params): array
    {
        $this->jobs->cancel($this->auth->requireUser($request->bearerToken()), (int) $params['id']);
        return ['cancelled' => true];
    }
    public function ticket(Request $request, array $params): array
    {
        $token = $request->bearerToken();
        $user = $this->auth->requireUser($token);
        return ['ticket' => $this->jobs->ticket($user, $token, (int) $params['id'], (int) $params['part'])];
    }
    public function download(Request $request, array $params): void
    {
        $file = $this->jobs->redeem((string) $request->input('ticket', ''), (int) $params['id'], (int) $params['part']);
        header('Content-Type: ' . (str_ends_with($file['name'], '.txt') ? 'text/plain; charset=utf-8' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'));
        header('Content-Disposition: attachment; filename="' . $file['name'] . '"');
        header('Cache-Control: private, no-store');
        header('Referrer-Policy: no-referrer');
        header('X-Content-Type-Options: nosniff');
        header('Content-Length: ' . filesize($file['path']));
        readfile($file['path']);
    }
}

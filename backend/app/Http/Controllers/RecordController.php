<?php

namespace App\Http\Controllers;

use App\Core\Request;
use App\Services\AuthService;
use App\Services\RecordService;

final class RecordController
{
    public function __construct(private AuthService $auth, private RecordService $records)
    {
    }

    /** @return array<string, mixed> */
    public function index(Request $request): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        return ['records' => $this->records->list($user, [
            'status' => (string) $request->input('status', ''),
            'dateFrom' => (string) $request->input('dateFrom', ''),
            'dateTo' => (string) $request->input('dateTo', ''),
            'keyword' => (string) $request->input('keyword', ''),
            'companyId' => (string) $request->input('companyId', ''),
            'inspectorName' => (string) $request->input('inspectorName', ''),
            'deliveryPerson' => (string) $request->input('deliveryPerson', ''),
            'customerType' => (string) $request->input('customerType', ''),
            'checkResult' => (string) $request->input('checkResult', ''),
            'customerName' => (string) $request->input('customerName', ''),
            'customerPhone' => (string) $request->input('customerPhone', ''),
            'customerAddress' => (string) $request->input('customerAddress', ''),
        ])];
    }

    /** @param array<string, string> $params @return array<string, mixed> */
    public function show(Request $request, array $params): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        return ['record' => $this->records->show((int) $params['id'], $user)];
    }

    /** @return array<string, mixed> */
    public function store(Request $request): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        $id = $this->records->create($user, $request->json());
        return ['id' => $id];
    }

    /** @param array<string, string> $params @return array<string, mixed> */
    public function rectify(Request $request, array $params): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        $this->records->rectify((int) $params['id'], $user, $request->json());
        return ['id' => (int) $params['id']];
    }
}

<?php

namespace App\Http\Controllers;

use App\Core\Request;
use App\Services\AuthService;
use App\Services\FileStorageService;
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
        $filters = [
            'status' => (string) $request->input('status', ''),
            'dateFrom' => (string) $request->input('dateFrom', ''),
            'dateTo' => (string) $request->input('dateTo', ''),
            'keyword' => (string) $request->input('keyword', ''),
            'companyId' => (string) $request->input('companyId', ''),
            'regionId' => (string) $request->input('regionId', ''),
            'storeId' => (string) $request->input('storeId', ''),
            'inspectorName' => (string) $request->input('inspectorName', ''),
            'deliveryPerson' => (string) $request->input('deliveryPerson', ''),
            'customerType' => (string) $request->input('customerType', ''),
            'checkResult' => (string) $request->input('checkResult', ''),
            'hazardCode' => (string) $request->input('hazardCode', ''),
            'hazardCodes' => $request->input('hazardCodes', []),
            'gasSourceIssues' => $request->input('gasSourceIssues', []),
            'customerName' => (string) $request->input('customerName', ''),
            'customerPhone' => (string) $request->input('customerPhone', ''),
            'customerAddress' => (string) $request->input('customerAddress', ''),
        ];
        if ($request->input('page', null) !== null) {
            $page = filter_var($request->input('page'), FILTER_VALIDATE_INT);
            $pageSize = filter_var($request->input('pageSize', 50), FILTER_VALIDATE_INT);
            if ($page === false || $page < 1 || $pageSize === false || $pageSize < 1 || $pageSize > 100) {
                throw new \App\Core\HttpException(422, '分页参数无效，每页须为 1 至 100 条。');
            }
            return $this->records->page($user, $filters, $page, $pageSize);
        }
        return ['records' => $this->records->list($user, $filters)];
    }

    /** @return array<string, mixed> */
    public function organizationOptions(Request $request): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        return $this->records->organizationOptions($user);
    }

    /** @param array<string, string> $params @return array<string, mixed> */
    public function show(Request $request, array $params): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        $record = $this->records->show((int) $params['id'], $user);
        $record['canSupplementRectificationPhoto'] = $record['status'] === 'rectified';
        return ['record' => $record];
    }

    /** @return array<string, mixed> */
    public function store(Request $request): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        $id = $this->records->create($user, $request->json());
        return ['id' => $id];
    }

    /** @return array<string, mixed> */
    public function saveDraft(Request $request): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        return ['id' => $this->records->saveDraft($user, $request->json())];
    }

    /** @return array<string, mixed> */
    public function submitDraft(Request $request): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        return ['id' => $this->records->submitDraft($user, $request->json())];
    }

    /** @param array<string, string> $params @return array<string, mixed> */
    public function rectify(Request $request, array $params): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        $this->records->rectify((int) $params['id'], $user, $request->json());
        return ['id' => (int) $params['id']];
    }

    /** @param array<string, string> $params @return array<string, mixed> */
    public function supplementRectificationPhoto(Request $request, array $params, FileStorageService $storage): array
    {
        $user = $this->auth->requireUser($request->bearerToken());
        $id = (int) $params['id'];
        $this->records->supplementRectificationPhoto($id, $user, [
            'operationId' => $request->input('operationId'),
            'reason' => $request->input('reason'),
            'replacePhotoId' => $request->input('replacePhotoId'),
        ], $storage->storeUploadedFile(...));
        return ['id' => $id];
    }
}

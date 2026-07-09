<?php

namespace App\Services;

use App\Core\HttpException;
use App\Domain\HazardCatalog;
use App\Repositories\RecordRepository;

final class RecordService
{
    public function __construct(private RecordRepository $records)
    {
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $filters @return list<array<string, mixed>> */
    public function list(array $user, array $filters = []): array
    {
        return $this->records->listForUser($user, $filters);
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $filters @return list<array<string, mixed>> */
    public function export(array $user, array $filters = []): array
    {
        $records = $this->records->listForUser($user, $filters, 5000);
        return array_map(fn (array $record): array => $this->show((int) $record['id'], $user), $records);
    }

    /** @param array<string, mixed> $user @return array<string, mixed> */
    public function show(int $id, array $user): array
    {
        $record = $this->records->findForUser($id, $user);
        if (!$record) {
            throw new HttpException(404, '记录不存在，或你没有权限查看。');
        }
        return $record;
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    public function create(array $user, array $payload): int
    {
        $this->validateCreate($payload);
        return $this->records->create($user, $payload);
    }

    /** @param array<string, mixed> $user @param array<string, mixed> $payload */
    public function rectify(int $id, array $user, array $payload): void
    {
        if (empty($payload['rectificationDate'])) {
            throw new HttpException(422, '请填写整改日期。');
        }
        $this->records->rectify($id, $user, $payload);
    }

    /** @param array<string, mixed> $payload */
    private function validateCreate(array $payload): void
    {
        $required = [
            'customerName' => '用户名',
            'customerPhone' => '电话',
            'customerType' => '用户类型',
            'customerAddress' => '地址',
            'inspectorName' => '检查人',
            'deliveryPerson' => '配送员',
            'inspectionDate' => '检查日期',
            'checkResult' => '检查结果',
        ];

        $errors = [];
        foreach ($required as $key => $label) {
            if (trim((string) ($payload[$key] ?? '')) === '') {
                $errors[$key] = "{$label}不能为空";
            }
        }

        if (!in_array($payload['customerType'] ?? '', HazardCatalog::customerTypes(), true)) {
            $errors['customerType'] = '用户类型不正确';
        }

        $validResults = [
            HazardCatalog::RESULT_CLEARED,
            HazardCatalog::RESULT_NOT_CLEARED,
            HazardCatalog::RESULT_RECTIFIED,
        ];
        if (!in_array($payload['checkResult'] ?? '', $validResults, true)) {
            $errors['checkResult'] = '检查结果不正确';
        }

        $hazards = $payload['hazardItems'] ?? [];
        $gasSourceIssues = $payload['gasSourceIssues'] ?? [];
        if (($payload['checkResult'] ?? '') === HazardCatalog::RESULT_NOT_CLEARED
            && count($hazards) === 0
            && count($gasSourceIssues) === 0) {
            $errors['hazardItems'] = '隐患未清零时必须选择至少一个隐患项目或气源排查异常';
        }

        if ($errors) {
            throw new HttpException(422, '提交内容不完整，请补充后再提交。', $errors);
        }
    }
}

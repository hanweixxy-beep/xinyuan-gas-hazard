<?php

namespace App\Http\Controllers;

use App\Core\Request;
use App\Domain\ExportColumns;
use App\Services\AuthService;
use App\Services\ExportService;
use App\Services\RecordService;

final class ExportController
{
    public function __construct(
        private AuthService $auth,
        private RecordService $records,
        private ExportService $export
    ) {
    }

    /** @return array<string, mixed> */
    public function columns(): array
    {
        return ['columns' => ExportColumns::all()];
    }

    public function xlsx(Request $request): void
    {
        $user = $this->auth->requireUser($request->bearerToken());
        $file = $this->export->recordsWorkbookFile($this->records->export($user, [
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
        ]));

        $filename = 'xinyuan-gas-records-' . date('Ymd-His') . '.xlsx';
        header('Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
        header('Content-Disposition: attachment; filename="' . $filename . '"');
        header('Cache-Control: no-store');
        header('Content-Length: ' . filesize($file));
        readfile($file);
        @unlink($file);
    }
}

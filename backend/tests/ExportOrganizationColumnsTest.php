<?php

use App\Core\Autoloader;
use App\Domain\ExportColumns;
use App\Services\ExportService;

require dirname(__DIR__) . '/app/Core/Autoloader.php';
Autoloader::register(dirname(__DIR__) . '/app');

$columns = ExportColumns::all();
$lastColumns = array_slice(array_column($columns, 'label'), -2);
if ($lastColumns !== ['片区', '门店']) {
    throw new RuntimeException('Region and store columns must be appended to preserve the existing column order.');
}

$service = new ExportService();
$file = $service->recordsWorkbookFile([[
    'id' => 1,
    'region_name' => '相山测试片区',
    'store_name' => '南湖测试门店',
    'customer_name' => '导出测试用户',
    'customer_phone' => '19900000000',
    'customer_type' => '居民用户',
    'customer_address' => '导出测试地址',
    'inspector_name' => '导出测试员',
    'inspection_date' => '2026-09-03',
    'check_result' => '隐患已清零',
    'delivery_person' => '测试配送员',
    'hazards' => [],
    'gasSourceIssues' => [],
    'photos' => [],
]]);

try {
    $zip = new ZipArchive();
    if ($zip->open($file) !== true) {
        throw new RuntimeException('Generated workbook could not be opened.');
    }
    $sheet = (string) $zip->getFromName('xl/worksheets/sheet1.xml');
    $zip->close();

    foreach (['片区', '门店', '相山测试片区', '南湖测试门店'] as $expected) {
        if (!str_contains($sheet, $expected)) {
            throw new RuntimeException("Workbook is missing expected value: {$expected}");
        }
    }
    if (!str_contains($sheet, 'autoFilter ref="A1:AD2"')) {
        throw new RuntimeException('Workbook filter range must include the two appended organization columns.');
    }
} finally {
    @unlink($file);
}

echo "Export organization columns test passed.\n";

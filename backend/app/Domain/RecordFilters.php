<?php

namespace App\Domain;

use App\Core\HttpException;

final class RecordFilters
{
    public const TEXT_KEYS = ['status', 'dateFrom', 'dateTo', 'keyword', 'companyId', 'regionId', 'storeId',
        'inspectorName', 'deliveryPerson', 'customerType', 'checkResult', 'hazardCode',
        'customerName', 'customerPhone', 'customerAddress'];

    public static function selections(mixed $value): array
    {
        if ($value === null || $value === '') return [];
        $items = is_array($value) ? $value : [$value];
        if (count($items) > 30) throw new HttpException(422, '每组筛选最多选择 30 项。');
        $result = [];
        foreach ($items as $item) {
            if (!is_string($item) || strlen($item) > 500) throw new HttpException(422, '多选筛选格式不正确。');
            $item = trim($item);
            if ($item !== '') $result[] = $item;
        }
        return array_values(array_unique($result));
    }

    public static function normalize(array $input): array
    {
        $filters = [];
        foreach (self::TEXT_KEYS as $key) {
            $value = $input[$key] ?? '';
            if (!is_scalar($value) || strlen((string) $value) > 500) throw new HttpException(422, '筛选条件格式不正确。');
            $filters[$key] = trim((string) $value);
        }
        $filters['hazardCodes'] = self::selections($input['hazardCodes'] ?? []);
        $filters['gasSourceIssues'] = self::selections($input['gasSourceIssues'] ?? []);
        return $filters;
    }
}

<?php

namespace App\Domain;

final class HazardCatalog
{
    public const RESULT_CLEARED = '隐患已清零';
    public const RESULT_NOT_CLEARED = '隐患未清零';
    public const RESULT_RECTIFIED = '隐患已整改';

    /** @return list<string> */
    public static function customerTypes(): array
    {
        return ['居民用户', '商业用户', '餐饮用户', '工业用户', '流动摊贩'];
    }

    /** @return list<array{code:string, category:string, name:string}> */
    public static function hazardItems(): array
    {
        return [
            ['code' => 'alarm_missing', 'category' => '可燃气体报警器', 'name' => '未安装可燃气体报警器（重大隐患）'],
            ['code' => 'alarm_fault', 'category' => '可燃气体报警器', 'name' => '可燃气体报警器故障需更换'],
            ['code' => 'alarm_wrong_gas', 'category' => '可燃气体报警器', 'name' => '可燃气体报警器检测气体类型错误'],
            ['code' => 'alarm_site_fix', 'category' => '可燃气体报警器', 'name' => '如报警器未插电或安装位置不对，现场整改，不做统计'],
            ['code' => 'hose_tee', 'category' => '软管', 'name' => '有三通（重大隐患）'],
            ['code' => 'hose_non_standard', 'category' => '软管', 'name' => '非国标软管（重大隐患）'],
            ['code' => 'hose_hidden', 'category' => '软管', 'name' => '软管穿墙或暗埋'],
            ['code' => 'hose_no_clamp', 'category' => '软管', 'name' => '软管未用管箍固定（重大隐患）'],
            ['code' => 'hose_too_long', 'category' => '软管', 'name' => '软管长度超过2米'],
            ['code' => 'environment_bad', 'category' => '用气环境', 'name' => '在地下室、半地下室等通风不畅的场所使用瓶装液化气（重大隐患）'],
            ['code' => 'valve_adjustable', 'category' => '减压阀', 'name' => '使用可调式减压阀'],
            ['code' => 'guide_wrong', 'category' => '安全用气指南', 'name' => '未正确填写并张贴安全用气指南'],
        ];
    }

    /** @return list<string> */
    public static function gasSourceIssues(): array
    {
        return [
            '钢瓶扫码无信息',
            '钢瓶无二维码',
            '钢瓶超期或报废',
            '钢瓶充装日期与客户实际使用周期不符',
            '钢瓶为非鑫远公司气瓶',
            '虚假安检',
        ];
    }

    /** @return array<string, array{code:string, category:string, name:string}> */
    public static function hazardMap(): array
    {
        $map = [];
        foreach (self::hazardItems() as $item) {
            $map[$item['code']] = $item;
        }
        return $map;
    }
}


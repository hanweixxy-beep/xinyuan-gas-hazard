<?php

namespace App\Services;

use App\Domain\ExportColumns;
use App\Domain\HazardCatalog;
use RuntimeException;
use ZipArchive;

final class ExportService
{
    /** @var array<string, array{issue:string, photo:string}> */
    private const CATEGORY_COLUMNS = [
        '可燃气体报警器' => ['issue' => 'alarmIssue', 'photo' => 'alarmPhoto'],
        '软管' => ['issue' => 'hoseIssue', 'photo' => 'hosePhoto'],
        '用气环境' => ['issue' => 'environmentIssue', 'photo' => 'environmentPhoto'],
        '减压阀' => ['issue' => 'valveIssue', 'photo' => 'valvePhoto'],
        '安全用气指南' => ['issue' => 'guideIssue', 'photo' => 'guidePhoto'],
    ];

    private const PHOTO_KEYS = [
        'storefrontPhoto',
        'alarmPhoto',
        'hosePhoto',
        'environmentPhoto',
        'valvePhoto',
        'guidePhoto',
        'cylinderQrPhoto',
        'cylinderPhoto',
        'rectificationPhoto',
    ];

    private const IMAGE_TYPES = [
        'jpg' => 'image/jpeg',
        'jpeg' => 'image/jpeg',
        'png' => 'image/png',
    ];

    public function __construct(private string $uploadRoot = '')
    {
    }

    /** @param list<array<string, mixed>> $records */
    public function recordsWorkbook(array $records): string
    {
        $tempFile = $this->recordsWorkbookFile($records);
        $content = file_get_contents($tempFile);
        @unlink($tempFile);
        if ($content === false) {
            throw new RuntimeException('读取 xlsx 文件失败。');
        }

        return $content;
    }

    /** @param list<array<string, mixed>> $records */
    public function recordsWorkbookFile(array $records): string
    {
        $columns = ExportColumns::all();
        $sheetRows = [
            array_map(static fn (array $column): string => $column['label'], $columns),
        ];
        $images = [];

        foreach ($records as $index => $record) {
            foreach ($this->recordRows($record, $index + 1) as $row) {
                $excelRow = count($sheetRows) + 1;
                $sheetRow = [];
                foreach ($columns as $columnIndex => $column) {
                    $key = $column['key'];
                    $value = (string) ($row[$key] ?? '');
                    $image = in_array($key, self::PHOTO_KEYS, true) ? $this->imageFromPath($value) : null;
                    if ($image !== null) {
                        $image['row'] = $excelRow;
                        $image['column'] = $columnIndex + 1;
                        $images[] = $image;
                        $value = '';
                    }
                    $sheetRow[] = $value;
                }
                $sheetRows[] = $sheetRow;
            }
        }

        return $this->buildXlsxFile($sheetRows, $images);
    }

    /** @param array<string, mixed> $record @return list<array<string, string>> */
    private function recordRows(array $record, int $sequence): array
    {
        $photosByType = $this->photosByType($record['photos'] ?? []);
        $hazardPhotosByCode = $this->hazardPhotosByCode($photosByType['hazard'] ?? []);
        $hazardLines = $this->hazardLines($record['hazards'] ?? [], $hazardPhotosByCode);

        $storefrontPhotos = $this->photoPaths($photosByType['storefront'] ?? []);
        $cylinderQrPhotos = $this->photoPaths($photosByType['cylinder_qr'] ?? []);
        $cylinderPhotos = $this->photoPaths($photosByType['cylinder'] ?? []);
        $rectificationPhotos = $this->photoPaths($photosByType['rectification'] ?? []);
        $gasSourceIssues = array_values(array_filter(array_map(
            static fn (array $issue): string => (string) ($issue['issue_text'] ?? ''),
            $record['gasSourceIssues'] ?? []
        )));

        $lineCounts = [
            count($storefrontPhotos),
            count($cylinderQrPhotos),
            count($cylinderPhotos),
            count($rectificationPhotos),
            count($gasSourceIssues),
        ];
        foreach ($hazardLines as $items) {
            $lineCounts[] = count($items);
        }

        $rowCount = max(1, ...$lineCounts);
        $rows = [];
        for ($line = 0; $line < $rowCount; $line++) {
            $row = [
                'sequence' => (string) $sequence,
                'customerName' => (string) ($record['customer_name'] ?? ''),
                'storefrontPhoto' => $storefrontPhotos[$line] ?? '',
                'customerPhone' => (string) ($record['customer_phone'] ?? ''),
                'customerType' => (string) ($record['customer_type'] ?? ''),
                'customerAddress' => (string) ($record['customer_address'] ?? ''),
                'inspectorName' => (string) ($record['inspector_name'] ?? ''),
                'inspectionDate' => (string) ($record['inspection_date'] ?? ''),
                'checkResult' => (string) ($record['check_result'] ?? ''),
                'deliveryPerson' => (string) ($record['delivery_person'] ?? ''),
                'remark' => (string) ($record['remark'] ?? ''),
                'gasSourceIssue' => $gasSourceIssues[$line] ?? '',
                'cylinderQrPhoto' => $cylinderQrPhotos[$line] ?? '',
                'cylinderPhoto' => $cylinderPhotos[$line] ?? '',
                'rectificationDate' => (string) ($record['rectification_date'] ?? ''),
                'rectificationNote' => (string) ($record['rectification_note'] ?? ''),
                'rectificationPerson' => (string) ($record['rectification_person'] ?? ''),
                'rectificationPhoto' => $rectificationPhotos[$line] ?? '',
            ];

            foreach (self::CATEGORY_COLUMNS as $category => $columns) {
                $item = $hazardLines[$category][$line] ?? ['issue' => '', 'photo' => ''];
                $row[$columns['issue']] = $item['issue'];
                $row[$columns['photo']] = $item['photo'];
            }

            $rows[] = $row;
        }

        return $rows;
    }

    /** @param list<array<string, mixed>> $hazards @param array<string, list<array<string, mixed>>> $photosByCode @return array<string, list<array{issue:string, photo:string}>> */
    private function hazardLines(array $hazards, array $photosByCode): array
    {
        $lines = array_fill_keys(array_keys(self::CATEGORY_COLUMNS), []);
        $usedCodes = [];

        foreach ($hazards as $hazard) {
            $category = (string) ($hazard['hazard_category'] ?? '');
            if (!isset($lines[$category])) {
                continue;
            }

            $code = (string) ($hazard['hazard_code'] ?? '');
            $name = (string) ($hazard['hazard_name'] ?? '');
            $photos = $code !== '' ? $this->photoPaths($photosByCode[$code] ?? []) : [];
            $usedCodes[$code] = true;

            if ($photos === []) {
                $lines[$category][] = ['issue' => $name, 'photo' => ''];
                continue;
            }

            foreach ($photos as $photo) {
                $lines[$category][] = ['issue' => $name, 'photo' => $photo];
            }
        }

        $hazardMap = HazardCatalog::hazardMap();
        foreach ($photosByCode as $code => $photos) {
            if (isset($usedCodes[$code])) {
                continue;
            }

            $item = $hazardMap[$code] ?? null;
            $category = $item['category'] ?? '';
            if (!isset($lines[$category])) {
                continue;
            }

            foreach ($this->photoPaths($photos) as $photo) {
                $lines[$category][] = [
                    'issue' => (string) ($item['name'] ?? ''),
                    'photo' => $photo,
                ];
            }
        }

        return $lines;
    }

    /** @param list<array<string, mixed>> $photos @return array<string, list<array<string, mixed>>> */
    private function photosByType(array $photos): array
    {
        $grouped = [];
        foreach ($photos as $photo) {
            $type = (string) ($photo['photo_type'] ?? '');
            if ($type === '') {
                continue;
            }
            $grouped[$type][] = $photo;
        }
        return $grouped;
    }

    /** @param list<array<string, mixed>> $photos @return array<string, list<array<string, mixed>>> */
    private function hazardPhotosByCode(array $photos): array
    {
        $grouped = [];
        foreach ($photos as $photo) {
            $code = (string) ($photo['hazard_code'] ?? '');
            if ($code === '') {
                continue;
            }
            $grouped[$code][] = $photo;
        }
        return $grouped;
    }

    /** @param list<array<string, mixed>> $photos @return list<string> */
    private function photoPaths(array $photos): array
    {
        return array_values(array_filter(array_map(
            static fn (array $photo): string => (string) ($photo['file_path'] ?? ''),
            $photos
        )));
    }

    /** @return array{path:string, extension:string, contentType:string}|null */
    private function imageFromPath(string $filePath): ?array
    {
        if ($filePath === '' || $this->uploadRoot === '') {
            return null;
        }

        $normalized = str_replace('\\', '/', $filePath);
        if (!str_starts_with($normalized, '/uploads/')) {
            return null;
        }

        $extension = strtolower(pathinfo($normalized, PATHINFO_EXTENSION));
        if (!isset(self::IMAGE_TYPES[$extension])) {
            return null;
        }

        $relative = substr($normalized, strlen('/uploads/'));
        $fullPath = rtrim($this->uploadRoot, '/\\')
            . DIRECTORY_SEPARATOR
            . str_replace('/', DIRECTORY_SEPARATOR, $relative);

        if (!is_file($fullPath) || !is_readable($fullPath)) {
            return null;
        }

        return [
            'path' => $fullPath,
            'extension' => $extension,
            'contentType' => self::IMAGE_TYPES[$extension],
        ];
    }

    /**
     * @param list<list<string>> $rows
     * @param list<array{path:string, extension:string, contentType:string, row:int, column:int}> $images
     */
    private function buildXlsx(array $rows, array $images): string
    {
        $tempFile = $this->buildXlsxFile($rows, $images);
        $content = file_get_contents($tempFile);
        @unlink($tempFile);
        if ($content === false) {
            throw new RuntimeException('读取 xlsx 文件失败。');
        }

        return $content;
    }

    /**
     * @param list<list<string>> $rows
     * @param list<array{path:string, extension:string, contentType:string, row:int, column:int}> $images
     */
    private function buildXlsxFile(array $rows, array $images): string
    {
        if (!class_exists(ZipArchive::class)) {
            throw new RuntimeException('服务器缺少 php-zip 扩展，无法生成 xlsx 文件。');
        }

        $tempFile = tempnam(sys_get_temp_dir(), 'xinyuan-export-');
        if ($tempFile === false) {
            throw new RuntimeException('无法创建导出临时文件。');
        }

        $zip = new ZipArchive();
        if ($zip->open($tempFile, ZipArchive::OVERWRITE) !== true) {
            @unlink($tempFile);
            throw new RuntimeException('无法创建 xlsx 压缩包。');
        }

        $zip->addFromString('[Content_Types].xml', $this->contentTypesXml($images));
        $zip->addFromString('_rels/.rels', $this->rootRelsXml());
        $zip->addFromString('docProps/app.xml', $this->appXml());
        $zip->addFromString('docProps/core.xml', $this->coreXml());
        $zip->addFromString('xl/workbook.xml', $this->workbookXml());
        $zip->addFromString('xl/_rels/workbook.xml.rels', $this->workbookRelsXml());
        $zip->addFromString('xl/styles.xml', $this->stylesXml());
        $zip->addFromString('xl/worksheets/sheet1.xml', $this->worksheetXml($rows, $images));
        if ($images !== []) {
            $zip->addFromString('xl/worksheets/_rels/sheet1.xml.rels', $this->sheetRelsXml());
            $zip->addFromString('xl/drawings/drawing1.xml', $this->drawingXml($images));
            $zip->addFromString('xl/drawings/_rels/drawing1.xml.rels', $this->drawingRelsXml($images));
            foreach ($images as $index => $image) {
                $zip->addFile($image['path'], 'xl/media/' . $this->imagePartName($image, $index + 1));
            }
        }
        $zip->close();

        return $tempFile;
    }

    /**
     * @param list<list<string>> $rows
     * @param list<array{path:string, extension:string, contentType:string, row:int, column:int}> $images
     */
    private function worksheetXml(array $rows, array $images): string
    {
        $imageRows = array_fill_keys(array_map(static fn (array $image): int => $image['row'], $images), true);
        $rowXml = '';
        foreach ($rows as $rowIndex => $row) {
            $excelRow = $rowIndex + 1;
            $cells = '';
            foreach ($row as $columnIndex => $value) {
                $cells .= $this->cell($this->columnName($columnIndex + 1) . $excelRow, $value, $excelRow === 1);
            }
            $rowAttributes = ' r="' . $excelRow . '"';
            if (isset($imageRows[$excelRow])) {
                $rowAttributes .= ' ht="88" customHeight="1"';
            }
            $rowXml .= '<row' . $rowAttributes . '>' . $cells . '</row>';
        }

        $lastRow = max(1, count($rows));
        $lastColumn = $this->columnName(count(ExportColumns::all()));

        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            . 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            . '<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>'
            . '<cols><col min="1" max="1" width="8" customWidth="1"/><col min="2" max="' . count(ExportColumns::all()) . '" width="22" customWidth="1"/></cols>'
            . '<sheetData>' . $rowXml . '</sheetData>'
            . '<autoFilter ref="A1:' . $lastColumn . $lastRow . '"/>'
            . ($images === [] ? '' : '<drawing r:id="rId1"/>')
            . '</worksheet>';
    }

    private function cell(string $reference, string $value, bool $header = false): string
    {
        $style = $header ? ' s="1"' : '';
        if ($value === '') {
            return '<c r="' . $reference . '"' . $style . '/>';
        }

        return '<c r="' . $reference . '" t="inlineStr"' . $style . '><is><t>'
            . $this->xml($value)
            . '</t></is></c>';
    }

    private function columnName(int $index): string
    {
        $name = '';
        while ($index > 0) {
            $index--;
            $name = chr(65 + ($index % 26)) . $name;
            $index = intdiv($index, 26);
        }
        return $name;
    }

    /** @param array{extension:string} $image */
    private function imagePartName(array $image, int $index): string
    {
        return 'image' . $index . '.' . $image['extension'];
    }

    /** @param list<array{path:string, extension:string, contentType:string, row:int, column:int}> $images */
    private function drawingXml(array $images): string
    {
        $anchors = '';
        foreach ($images as $index => $image) {
            $id = $index + 1;
            $fromColumn = max(0, $image['column'] - 1);
            $fromRow = max(0, $image['row'] - 1);
            $toColumn = $fromColumn + 1;
            $toRow = $fromRow + 1;
            $name = '照片' . $id;

            $anchors .= '<xdr:twoCellAnchor editAs="oneCell">'
                . '<xdr:from><xdr:col>' . $fromColumn . '</xdr:col><xdr:colOff>50000</xdr:colOff><xdr:row>' . $fromRow . '</xdr:row><xdr:rowOff>50000</xdr:rowOff></xdr:from>'
                . '<xdr:to><xdr:col>' . $toColumn . '</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>' . $toRow . '</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>'
                . '<xdr:pic>'
                . '<xdr:nvPicPr><xdr:cNvPr id="' . $id . '" name="' . $this->xml($name) . '"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr>'
                . '<xdr:blipFill><a:blip r:embed="rId' . $id . '"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>'
                . '<xdr:spPr><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr>'
                . '</xdr:pic><xdr:clientData/></xdr:twoCellAnchor>';
        }

        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" '
            . 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" '
            . 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            . $anchors
            . '</xdr:wsDr>';
    }

    /** @param list<array{extension:string}> $images */
    private function drawingRelsXml(array $images): string
    {
        $relationships = '';
        foreach ($images as $index => $image) {
            $id = $index + 1;
            $relationships .= '<Relationship Id="rId' . $id . '" '
                . 'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" '
                . 'Target="../media/' . $this->imagePartName($image, $id) . '"/>';
        }

        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . $relationships
            . '</Relationships>';
    }

    private function sheetRelsXml(): string
    {
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/>'
            . '</Relationships>';
    }

    private function xml(string $value): string
    {
        return htmlspecialchars($value, ENT_QUOTES | ENT_XML1, 'UTF-8');
    }

    /** @param list<array<string, mixed>> $images */
    private function contentTypesXml(array $images): string
    {
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            . '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            . '<Default Extension="xml" ContentType="application/xml"/>'
            . '<Default Extension="jpg" ContentType="image/jpeg"/>'
            . '<Default Extension="jpeg" ContentType="image/jpeg"/>'
            . '<Default Extension="png" ContentType="image/png"/>'
            . '<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>'
            . '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>'
            . '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            . '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
            . '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
            . ($images === [] ? '' : '<Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>')
            . '</Types>';
    }

    private function rootRelsXml(): string
    {
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            . '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>'
            . '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>'
            . '</Relationships>';
    }

    private function workbookXml(): string
    {
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            . 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            . '<sheets><sheet name="检查记录" sheetId="1" r:id="rId1"/></sheets>'
            . '</workbook>';
    }

    private function workbookRelsXml(): string
    {
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
            . '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
            . '</Relationships>';
    }

    private function stylesXml(): string
    {
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            . '<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>'
            . '<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE8F4EE"/><bgColor indexed="64"/></patternFill></fill></fills>'
            . '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>'
            . '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
            . '<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs>'
            . '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>'
            . '</styleSheet>';
    }

    private function appXml(): string
    {
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" '
            . 'xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">'
            . '<Application>淮北鑫远燃气隐患排查系统</Application>'
            . '</Properties>';
    }

    private function coreXml(): string
    {
        $createdAt = $this->xml(gmdate('Y-m-d\TH:i:s\Z'));
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
            . 'xmlns:dc="http://purl.org/dc/elements/1.1/" '
            . 'xmlns:dcterms="http://purl.org/dc/terms/" '
            . 'xmlns:dcmitype="http://purl.org/dc/dcmitype/" '
            . 'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
            . '<dc:creator>淮北鑫远燃气隐患排查系统</dc:creator>'
            . '<cp:lastModifiedBy>淮北鑫远燃气隐患排查系统</cp:lastModifiedBy>'
            . '<dcterms:created xsi:type="dcterms:W3CDTF">' . $createdAt . '</dcterms:created>'
            . '<dcterms:modified xsi:type="dcterms:W3CDTF">' . $createdAt . '</dcterms:modified>'
            . '</cp:coreProperties>';
    }
}

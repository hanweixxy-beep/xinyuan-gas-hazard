<?php

namespace App\Http\Controllers;

use App\Domain\ExportColumns;
use App\Domain\HazardCatalog;

final class CatalogController
{
    /** @return array<string, mixed> */
    public function index(): array
    {
        return [
            'customerTypes' => HazardCatalog::customerTypes(),
            'hazardItems' => HazardCatalog::hazardItems(),
            'gasSourceIssues' => HazardCatalog::gasSourceIssues(),
            'exportColumns' => ExportColumns::all(),
        ];
    }
}


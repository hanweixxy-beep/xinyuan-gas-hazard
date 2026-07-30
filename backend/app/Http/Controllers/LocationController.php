<?php

namespace App\Http\Controllers;

use App\Core\HttpException;
use App\Core\Request;
use App\Services\AuthService;
use App\Services\TencentMapService;

final class LocationController
{
    public function __construct(private AuthService $auth, private TencentMapService $maps)
    {
    }

    /** @return array<string, mixed> */
    public function reverseGeocode(Request $request): array
    {
        $this->auth->requireUser($request->bearerToken());

        $latitude = $request->input('latitude');
        $longitude = $request->input('longitude');
        if (!is_numeric($latitude) || !is_numeric($longitude)) {
            throw new HttpException(422, '经纬度不能为空，请重新定位。');
        }

        return $this->maps->reverseGeocode((float) $latitude, (float) $longitude);
    }
}

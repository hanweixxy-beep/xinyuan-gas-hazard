<?php

namespace App\ThirdParty\Coordtransform;

/**
 * Adapted from ivone-liu/coordtransform-php (MIT).
 *
 * @see https://github.com/ivone-liu/coordtransform-php
 */
final class CoordinateTransform
{
    private const PI = 3.1415926535897932384626;
    private const EARTH_RADIUS = 6378245.0;
    private const ECCENTRICITY = 0.00669342162296594323;

    /** @return array{0: float, 1: float} Longitude and latitude in GCJ-02. */
    public static function wgs84ToGcj02(float $longitude, float $latitude): array
    {
        if (!ChinaRegion::contains($longitude, $latitude)) {
            return [$longitude, $latitude];
        }

        $deltaLatitude = self::transformLatitude($longitude - 105.0, $latitude - 35.0);
        $deltaLongitude = self::transformLongitude($longitude - 105.0, $latitude - 35.0);
        $radianLatitude = $latitude / 180.0 * self::PI;
        $magic = sin($radianLatitude);
        $magic = 1 - self::ECCENTRICITY * $magic * $magic;
        $sqrtMagic = sqrt($magic);
        $deltaLatitude = ($deltaLatitude * 180.0)
            / ((self::EARTH_RADIUS * (1 - self::ECCENTRICITY)) / ($magic * $sqrtMagic) * self::PI);
        $deltaLongitude = ($deltaLongitude * 180.0)
            / (self::EARTH_RADIUS / $sqrtMagic * cos($radianLatitude) * self::PI);

        return [$longitude + $deltaLongitude, $latitude + $deltaLatitude];
    }

    private static function transformLatitude(float $longitude, float $latitude): float
    {
        $result = -100.0 + 2.0 * $longitude + 3.0 * $latitude
            + 0.2 * $latitude * $latitude
            + 0.1 * $longitude * $latitude
            + 0.2 * sqrt(abs($longitude));
        $result += (20.0 * sin(6.0 * $longitude * self::PI)
            + 20.0 * sin(2.0 * $longitude * self::PI)) * 2.0 / 3.0;
        $result += (20.0 * sin($latitude * self::PI)
            + 40.0 * sin($latitude / 3.0 * self::PI)) * 2.0 / 3.0;
        $result += (160.0 * sin($latitude / 12.0 * self::PI)
            + 320.0 * sin($latitude * self::PI / 30.0)) * 2.0 / 3.0;
        return $result;
    }

    private static function transformLongitude(float $longitude, float $latitude): float
    {
        $result = 300.0 + $longitude + 2.0 * $latitude
            + 0.1 * $longitude * $longitude
            + 0.1 * $longitude * $latitude
            + 0.1 * sqrt(abs($longitude));
        $result += (20.0 * sin(6.0 * $longitude * self::PI)
            + 20.0 * sin(2.0 * $longitude * self::PI)) * 2.0 / 3.0;
        $result += (20.0 * sin($longitude * self::PI)
            + 40.0 * sin($longitude / 3.0 * self::PI)) * 2.0 / 3.0;
        $result += (150.0 * sin($longitude / 12.0 * self::PI)
            + 300.0 * sin($longitude / 30.0 * self::PI)) * 2.0 / 3.0;
        return $result;
    }
}

<?php

namespace App\ThirdParty\Coordtransform;

/**
 * China-region bounds adapted from ivone-liu/coordtransform-php (MIT).
 */
final class ChinaRegion
{
    /** @var array<int, array{0: float, 1: float, 2: float, 3: float}> */
    private const REGIONS = [
        [79.446200, 49.220400, 96.330000, 42.889900],
        [109.687200, 54.141500, 135.000200, 39.374200],
        [73.124600, 42.889900, 124.143255, 29.529700],
        [82.968400, 29.529700, 97.035200, 26.718600],
        [97.025300, 29.529700, 124.367395, 20.414096],
        [107.975793, 20.414096, 111.744104, 17.871542],
    ];

    /** @var array<int, array{0: float, 1: float, 2: float, 3: float}> */
    private const EXCLUDED_REGIONS = [
        [119.921265, 25.398623, 122.497559, 21.785006],
        [101.865200, 22.284000, 106.665000, 20.098800],
        [106.452500, 21.542200, 108.051000, 20.487800],
        [109.032300, 55.817500, 119.127000, 50.325700],
        [127.456800, 55.817500, 137.022700, 49.557400],
        [131.266200, 44.892200, 137.022700, 42.569200],
        [73.124600, 35.398637, 77.948114, 29.529700],
    ];

    public static function contains(float $longitude, float $latitude): bool
    {
        foreach (self::REGIONS as $region) {
            if (!self::within($region, $longitude, $latitude)) {
                continue;
            }
            foreach (self::EXCLUDED_REGIONS as $excluded) {
                if (self::within($excluded, $longitude, $latitude)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** @param array{0: float, 1: float, 2: float, 3: float} $bounds */
    private static function within(array $bounds, float $longitude, float $latitude): bool
    {
        [$west, $north, $east, $south] = $bounds;
        return $west <= $longitude
            && $east >= $longitude
            && $north >= $latitude
            && $south <= $latitude;
    }
}

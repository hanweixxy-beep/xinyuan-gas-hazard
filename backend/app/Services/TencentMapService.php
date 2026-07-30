<?php

namespace App\Services;

use App\Core\Config;
use App\Core\HttpException;
use App\ThirdParty\Coordtransform\CoordinateTransform;

final class TencentMapService
{
    private const API_BASE = 'https://apis.map.qq.com';

    private string $key;

    public function __construct(Config $config)
    {
        $this->key = trim((string) $config->get('tencent_map.web_service_key', ''));
    }

    /** @return array<string, mixed> */
    public function reverseGeocode(float $latitude, float $longitude): array
    {
        $this->validateCoordinates($latitude, $longitude);
        if ($this->key === '') {
            throw new HttpException(503, '腾讯地图地址服务尚未配置。');
        }

        // Convert locally so one storefront record consumes only one Tencent
        // WebService request: the reverse-geocoding request itself.
        [$mapLongitude, $mapLatitude] = CoordinateTransform::wgs84ToGcj02($longitude, $latitude);
        $geocoded = $this->request('/ws/geocoder/v1/', [
            'location' => $this->coordinate($mapLatitude, $mapLongitude),
            'get_poi' => 1,
            'poi_options' => 'address_format=short;radius=200;page_size=5;page_index=1;policy=2',
            'key' => $this->key,
            'output' => 'json',
        ]);

        $result = $geocoded['result'] ?? null;
        if (!is_array($result)) {
            throw new HttpException(502, '腾讯地图未返回有效地址，请稍后重试。');
        }

        $address = $this->bestAddress($result);
        if ($address === '') {
            throw new HttpException(502, '腾讯地图未识别到当前位置的地址。');
        }

        return [
            'address' => $address,
            'provider' => 'tencent',
            'formattedAddress' => trim((string) ($result['formatted_addresses']['recommend'] ?? '')),
            'roughAddress' => trim((string) ($result['formatted_addresses']['rough'] ?? '')),
        ];
    }

    private function validateCoordinates(float $latitude, float $longitude): void
    {
        if (!is_finite($latitude) || $latitude < -90 || $latitude > 90) {
            throw new HttpException(422, '纬度不正确，请重新定位。');
        }
        if (!is_finite($longitude) || $longitude < -180 || $longitude > 180) {
            throw new HttpException(422, '经度不正确，请重新定位。');
        }
    }

    private function coordinate(float $latitude, float $longitude): string
    {
        return sprintf('%.7F,%.7F', $latitude, $longitude);
    }

    /** @param array<string, mixed> $result */
    private function bestAddress(array $result): string
    {
        $address = trim((string) ($result['address'] ?? ''));
        $recommend = trim((string) ($result['formatted_addresses']['recommend'] ?? ''));
        if ($recommend !== '') {
            $component = is_array($result['address_component'] ?? null)
                ? $result['address_component']
                : [];
            $prefix = '';
            foreach (['province', 'city'] as $key) {
                $part = trim((string) ($component[$key] ?? ''));
                if ($part !== '' && !str_contains($prefix, $part) && !str_starts_with($recommend, $part)) {
                    $prefix .= $part;
                }
            }
            return $prefix . $recommend;
        }
        if ($address !== '') {
            return $address;
        }

        $component = $result['address_component'] ?? [];
        if (!is_array($component)) {
            return '';
        }

        $parts = [];
        foreach (['province', 'city', 'district', 'street', 'street_number'] as $key) {
            $part = trim((string) ($component[$key] ?? ''));
            if ($part !== '' && !in_array($part, $parts, true)) {
                $parts[] = $part;
            }
        }
        return implode('', $parts);
    }

    /** @param array<string, scalar> $parameters
     *  @return array<string, mixed>
     */
    private function request(string $path, array $parameters): array
    {
        $url = self::API_BASE . $path . '?' . http_build_query($parameters, '', '&', PHP_QUERY_RFC3986);
        $curl = curl_init($url);
        if ($curl === false) {
            throw new HttpException(503, '服务器暂时无法连接腾讯地图。');
        }

        curl_setopt_array($curl, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_CONNECTTIMEOUT => 3,
            CURLOPT_TIMEOUT => 8,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_HTTPHEADER => ['Accept: application/json'],
            CURLOPT_USERAGENT => 'XinyuanGasHazard/1.0',
        ]);

        $body = curl_exec($curl);
        $statusCode = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        $error = curl_error($curl);
        curl_close($curl);

        if (!is_string($body) || $body === '' || $statusCode < 200 || $statusCode >= 300) {
            throw new HttpException(502, $error !== ''
                ? '腾讯地图网络连接失败，请稍后重试。'
                : '腾讯地图服务暂时不可用，请稍后重试。');
        }

        $decoded = json_decode($body, true);
        if (!is_array($decoded)) {
            throw new HttpException(502, '腾讯地图返回格式不正确，请稍后重试。');
        }

        $providerStatus = (int) ($decoded['status'] ?? -1);
        if ($providerStatus !== 0) {
            if ($providerStatus === 110) {
                throw new HttpException(502, '腾讯地图未授权当前服务器，请检查 Key 的 IP 白名单。');
            }
            throw new HttpException(502, '腾讯地图地址解析失败，请检查服务配置。', [
                'providerStatus' => $providerStatus,
                'providerMessage' => trim((string) ($decoded['message'] ?? '')),
                'providerPath' => $path,
            ]);
        }

        return $decoded;
    }
}

<?php

namespace App\Core;

final class Request
{
    /** @var array<string, mixed>|null */
    private ?array $json = null;

    public function method(): string
    {
        return strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
    }

    public function path(): string
    {
        $uri = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
        return '/' . trim($uri, '/');
    }

    /** @return array<string, mixed> */
    public function json(): array
    {
        if ($this->json !== null) {
            return $this->json;
        }

        $raw = file_get_contents('php://input') ?: '';
        if ($raw === '') {
            return $this->json = [];
        }

        $decoded = json_decode($raw, true);
        if (!is_array($decoded)) {
            throw new HttpException(400, '请求 JSON 格式不正确，请检查后重试。');
        }

        return $this->json = $decoded;
    }

    public function input(string $key, mixed $default = null): mixed
    {
        $body = $this->json();
        return $body[$key] ?? $_POST[$key] ?? $_GET[$key] ?? $default;
    }

    public function bearerToken(): ?string
    {
        $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
        if (preg_match('/Bearer\s+(.+)/i', $header, $matches)) {
            return trim($matches[1]);
        }

        return null;
    }

    public function ip(): string
    {
        return $_SERVER['REMOTE_ADDR'] ?? '';
    }

    public function userAgent(): string
    {
        return $_SERVER['HTTP_USER_AGENT'] ?? '';
    }
}


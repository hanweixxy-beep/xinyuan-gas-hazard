<?php

namespace App\Services;

use App\Core\Config;
use App\Core\HttpException;

final class FileStorageService
{
    private const MAX_SIZE = 10485760;
    private const ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/webp'];

    public function __construct(private Config $config)
    {
    }

    /** @return array<string, mixed> */
    public function storeUploadedFile(string $field = 'file'): array
    {
        if (!isset($_FILES[$field]) || !is_array($_FILES[$field])) {
            throw new HttpException(422, '请选择要上传的照片。');
        }

        $file = $_FILES[$field];
        if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
            throw new HttpException(422, '照片上传失败，请重新选择后再试。');
        }
        if (($file['size'] ?? 0) > self::MAX_SIZE) {
            throw new HttpException(422, '单张照片不能超过 10MB。');
        }

        $mime = mime_content_type($file['tmp_name']);
        if (!in_array($mime, self::ALLOWED_MIME, true)) {
            throw new HttpException(422, '只支持 JPG、PNG 或 WebP 照片。');
        }

        $datePath = date('Y/m/d');
        $root = rtrim((string) $this->config->get('upload_root'), '/\\');
        $targetDir = $root . DIRECTORY_SEPARATOR . str_replace('/', DIRECTORY_SEPARATOR, $datePath);
        if (!is_dir($targetDir) && !mkdir($targetDir, 0755, true)) {
            throw new HttpException(500, '上传目录不可写，请联系管理员检查服务器。');
        }

        $extension = match ($mime) {
            'image/png' => 'png',
            'image/webp' => 'webp',
            default => 'jpg',
        };
        $name = bin2hex(random_bytes(16)) . '.' . $extension;
        $target = $targetDir . DIRECTORY_SEPARATOR . $name;

        if (!move_uploaded_file($file['tmp_name'], $target)) {
            throw new HttpException(500, '照片保存失败，请稍后重试。');
        }

        return [
            'filePath' => '/uploads/' . $datePath . '/' . $name,
            'originalName' => $file['name'] ?? '',
            'mimeType' => $mime,
            'fileSize' => (int) $file['size'],
        ];
    }
}


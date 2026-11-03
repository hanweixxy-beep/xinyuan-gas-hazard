<?php

namespace App\Services;

use RuntimeException;

/** Creates disposable export copies; never changes source uploads. */
final class ExportImageService
{
    public function __construct(private string $uploadRoot, private string $cacheRoot)
    {
        if (!is_dir($cacheRoot) && !mkdir($cacheRoot, 0700, true) && !is_dir($cacheRoot)) {
            throw new RuntimeException('无法创建导出图片缓存。');
        }
    }

    /** @return array{path:string,extension:string,contentType:string}|null */
    public function prepare(string $filePath): ?array
    {
        $normalized = str_replace('\\', '/', $filePath);
        if (!str_starts_with($normalized, '/uploads/')) return null;
        $root = realpath($this->uploadRoot);
        $source = realpath($this->uploadRoot . '/' . substr($normalized, 9));
        if (!$root || !$source || !str_starts_with($source, $root . DIRECTORY_SEPARATOR) || !is_readable($source)) return null;
        $key = hash('sha256', 'v1-1600-78|' . $source . '|' . filemtime($source) . '|' . filesize($source));
        $target = $this->cacheRoot . '/' . $key . '.jpg';
        if (!is_file($target)) {
            $size = @getimagesize($source);
            // Decode one image at a time, with a bound on decoded memory.
            if (!$size || $size[0] < 1 || $size[1] < 1 || $size[0] * $size[1] > 24000000) return null;
            $image = match ($size[2]) {
                IMAGETYPE_JPEG => @imagecreatefromjpeg($source),
                IMAGETYPE_PNG => @imagecreatefrompng($source),
                default => false,
            };
            if (!$image) return null;
            $temp = null;
            try {
                $orientation = $size[2] === IMAGETYPE_JPEG && function_exists('exif_read_data')
                    ? (int) ((@exif_read_data($source)['Orientation'] ?? 1)) : 1;
                if (in_array($orientation, [2, 4, 5, 7], true)) imageflip($image, IMG_FLIP_HORIZONTAL);
                $angle = match ($orientation) { 3, 4 => 180, 6, 7 => -90, 5, 8 => 90, default => 0 };
                if ($angle) {
                    $rotated = imagerotate($image, $angle, 0);
                    if (!$rotated) throw new RuntimeException('图片旋转失败。');
                    imagedestroy($image);
                    $image = $rotated;
                }
                $edge = min(1600, max(imagesx($image), imagesy($image)));
                $temp = tempnam($this->cacheRoot, 'image-');
                if (!$temp) throw new RuntimeException('图片缓存空间不足。');
                do {
                    $ratio = min(1, $edge / max(imagesx($image), imagesy($image)));
                    $width = max(1, (int) round(imagesx($image) * $ratio));
                    $height = max(1, (int) round(imagesy($image) * $ratio));
                    $small = imagecreatetruecolor($width, $height);
                    try {
                        imagefill($small, 0, 0, imagecolorallocate($small, 255, 255, 255));
                        imagecopyresampled($small, $image, 0, 0, 0, 0, $width, $height, imagesx($image), imagesy($image));
                        if (!imagejpeg($small, $temp, 78)) throw new RuntimeException('生成导出照片失败。');
                    } finally {
                        imagedestroy($small);
                    }
                    clearstatcache(true, $temp);
                    $edge = (int) floor($edge * 0.8);
                } while (filesize($temp) > 350 * 1024 && $edge >= 480);
                if (!rename($temp, $target)) throw new RuntimeException('保存导出照片失败。');
            } finally {
                imagedestroy($image);
                if ($temp && is_file($temp)) @unlink($temp);
            }
        }
        @touch($target);
        return ['path' => $target, 'extension' => 'jpg', 'contentType' => 'image/jpeg'];
    }
}

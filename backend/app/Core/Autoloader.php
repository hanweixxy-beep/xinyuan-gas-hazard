<?php

namespace App\Core;

final class Autoloader
{
    public static function register(string $baseDir): void
    {
        spl_autoload_register(function (string $class) use ($baseDir): void {
            $prefix = 'App\\';
            if (strncmp($class, $prefix, strlen($prefix)) !== 0) {
                return;
            }

            $relative = substr($class, strlen($prefix));
            $file = rtrim($baseDir, '/\\') . DIRECTORY_SEPARATOR . str_replace('\\', DIRECTORY_SEPARATOR, $relative) . '.php';
            if (is_file($file)) {
                require $file;
            }
        });
    }
}


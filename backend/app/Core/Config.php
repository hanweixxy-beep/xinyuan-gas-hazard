<?php

namespace App\Core;

final class Config
{
    /** @var array<string, mixed> */
    private array $values;

    /** @param array<string, mixed> $values */
    public function __construct(array $values)
    {
        $this->values = $values;
    }

    public function get(string $key, mixed $default = null): mixed
    {
        $segments = explode('.', $key);
        $current = $this->values;

        foreach ($segments as $segment) {
            if (!is_array($current) || !array_key_exists($segment, $current)) {
                return $default;
            }
            $current = $current[$segment];
        }

        return $current;
    }
}


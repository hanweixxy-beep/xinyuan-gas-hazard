<?php

namespace App\Core;

use RuntimeException;

final class HttpException extends RuntimeException
{
    public function __construct(
        int $status,
        string $message,
        /** @var array<string, mixed> */
        private array $errors = []
    ) {
        parent::__construct($message, $status);
    }

    public function status(): int
    {
        return $this->getCode() ?: 500;
    }

    /** @return array<string, mixed> */
    public function errors(): array
    {
        return $this->errors;
    }
}


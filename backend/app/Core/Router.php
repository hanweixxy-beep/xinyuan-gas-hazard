<?php

namespace App\Core;

use Throwable;

final class Router
{
    /** @var list<array{method:string, pattern:string, handler:callable}> */
    private array $routes = [];

    public function add(string $method, string $pattern, callable $handler): void
    {
        $this->routes[] = [
            'method' => strtoupper($method),
            'pattern' => '/' . trim($pattern, '/'),
            'handler' => $handler,
        ];
    }

    public function dispatch(Request $request): void
    {
        try {
            foreach ($this->routes as $route) {
                if ($route['method'] !== $request->method()) {
                    continue;
                }

                $params = $this->match($route['pattern'], $request->path());
                if ($params === null) {
                    continue;
                }

                $result = call_user_func($route['handler'], $request, $params);
                if (is_array($result)) {
                    Response::json(['ok' => true, 'data' => $result]);
                    return;
                }

                return;
            }

            throw new HttpException(404, '接口不存在。');
        } catch (HttpException $exception) {
            Response::json([
                'ok' => false,
                'message' => $exception->getMessage(),
                'errors' => $exception->errors(),
            ], $exception->status());
        } catch (Throwable $exception) {
            Response::json([
                'ok' => false,
                'message' => '服务器处理失败，请稍后重试或联系管理员。',
            ], 500);
        }
    }

    /** @return array<string, string>|null */
    private function match(string $pattern, string $path): ?array
    {
        $names = [];
        $regex = preg_replace_callback('/\{([a-zA-Z_][a-zA-Z0-9_]*)\}/', function (array $matches) use (&$names): string {
            $names[] = $matches[1];
            return '([^/]+)';
        }, $pattern);

        if (!preg_match('#^' . $regex . '$#', $path, $matches)) {
            return null;
        }

        array_shift($matches);
        return array_combine($names, $matches) ?: [];
    }
}


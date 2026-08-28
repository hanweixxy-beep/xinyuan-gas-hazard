<?php

use App\Core\Autoloader;
use App\Core\Config;
use App\Core\Database;
use App\Core\Env;
use App\Core\Request;
use App\Core\Router;
use App\Http\Controllers\AdminController;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\CatalogController;
use App\Http\Controllers\ExportController;
use App\Http\Controllers\FileController;
use App\Http\Controllers\LocationController;
use App\Http\Controllers\RecordController;
use App\Repositories\CompanyRepository;
use App\Repositories\RecordRepository;
use App\Repositories\UserRepository;
use App\Services\AuthService;
use App\Services\AdminService;
use App\Services\ExportService;
use App\Services\FileStorageService;
use App\Services\RecordService;
use App\Services\TencentMapService;

require dirname(__DIR__) . '/app/Core/Autoloader.php';
Autoloader::register(dirname(__DIR__) . '/app');
Env::load(dirname(__DIR__) . '/.env');

$config = new Config(require dirname(__DIR__) . '/config/app.php');
$pdo = (new Database($config))->pdo();

$userRepository = new UserRepository($pdo);
$companyRepository = new CompanyRepository($pdo);
$recordRepository = new RecordRepository($pdo);

$authService = new AuthService($userRepository, $config);
$adminService = new AdminService($companyRepository, $userRepository);
$recordService = new RecordService($recordRepository);
$exportService = new ExportService((string) $config->get('upload_root'));
$fileStorage = new FileStorageService($config);
$tencentMapService = new TencentMapService($config);

$authController = new AuthController($authService);
$adminController = new AdminController($authService, $adminService);
$catalogController = new CatalogController();
$recordController = new RecordController($authService, $recordService);
$fileController = new FileController($authService, $fileStorage);
$locationController = new LocationController($authService, $tencentMapService);
$exportController = new ExportController($authService, $recordService, $exportService);

$router = new Router();

$router->add('GET', '/api/health', fn () => ['status' => 'ok', 'service' => 'xinyuan-gas-hazard-h5']);
$router->add('POST', '/api/auth/login', fn (Request $request) => $authController->login($request));
$router->add('GET', '/api/auth/me', fn (Request $request) => $authController->me($request));
$router->add('POST', '/api/auth/change-password', fn (Request $request) => $authController->changePassword($request));
$router->add('GET', '/api/admin/companies', fn (Request $request) => $adminController->companies($request));
$router->add('POST', '/api/admin/companies', fn (Request $request) => $adminController->createCompany($request));
$router->add('GET', '/api/admin/users', fn (Request $request) => $adminController->users($request));
$router->add('POST', '/api/admin/users', fn (Request $request) => $adminController->createUser($request));
$router->add('POST', '/api/admin/users/{id}/status', fn (Request $request, array $params) => $adminController->updateUserStatus($request, $params));
$router->add('POST', '/api/admin/users/{id}/access', fn (Request $request, array $params) => $adminController->updateUserAccess($request, $params));
$router->add('POST', '/api/admin/users/{id}/reset-password', fn (Request $request, array $params) => $adminController->resetPassword($request, $params));
$router->add('GET', '/api/catalog', fn () => $catalogController->index());
$router->add('GET', '/api/export/columns', fn () => $exportController->columns());
$router->add('GET', '/api/export/xlsx', fn (Request $request) => $exportController->xlsx($request));
$router->add('GET', '/api/records', fn (Request $request) => $recordController->index($request));
$router->add('POST', '/api/records', fn (Request $request) => $recordController->store($request));
$router->add('POST', '/api/records/drafts', fn (Request $request) => $recordController->saveDraft($request));
$router->add('POST', '/api/records/drafts/submit', fn (Request $request) => $recordController->submitDraft($request));
$router->add('GET', '/api/records/{id}', fn (Request $request, array $params) => $recordController->show($request, $params));
$router->add('POST', '/api/records/{id}/rectify', fn (Request $request, array $params) => $recordController->rectify($request, $params));
$router->add('POST', '/api/files/upload', fn (Request $request) => $fileController->upload($request));
$router->add('POST', '/api/location/reverse-geocode', fn (Request $request) => $locationController->reverseGeocode($request));

$router->dispatch(new Request());

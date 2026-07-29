# Deployment

## 服务器角色

云服务器只作为运行环境，不作为代码主仓库。代码以 GitHub 私有仓库为准，服务器部署前先从仓库或本地构建产物同步。

## 当前线上结构建议

```text
/var/www/xinyuan-gas/
  backend/
  frontend/
    admin/
    mobile/
  shared/
  uploads/
```

Nginx 应将：

- `/api/*` 转发到 `backend/public/index.php`
- `/admin/` 指向 `frontend/admin`
- `/mobile/` 指向 `frontend/mobile`
- `/uploads/` 指向上传照片目录

## 部署前检查

```bash
php -l backend/app/Repositories/RecordRepository.php
php -l backend/app/Http/Controllers/RecordController.php
php -l backend/app/Http/Controllers/ExportController.php
nginx -t
```

## 数据库

完整基础表结构位于 `database/schema.sql`，增量迁移脚本放在 `database/patches/`。

新服务器先导入基础表结构，再按文件日期顺序执行迁移脚本。生产执行前必须先备份数据库。

```bash
mysql -u root -p -e "CREATE DATABASE xinyuan_gas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p xinyuan_gas < database/schema.sql
```

Nginx 参考配置位于 `deploy/nginx/xinyuan-gas.conf.example`。

## Android APP

Android 工程在 `mobile-app/`。签名文件不进入仓库，需要从安全备份恢复到 `mobile-app/signing/`。

正式升级旧 APP 时必须使用原签名构建 release APK，并直接覆盖安装，不能先卸载。
云端化发布流程和旧数据迁移说明见
[`APP_CLOUD_FIRST_V2.md`](APP_CLOUD_FIRST_V2.md)。

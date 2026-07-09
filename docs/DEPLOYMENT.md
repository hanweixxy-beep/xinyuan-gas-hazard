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

数据库迁移脚本放在 `database/patches/`。生产执行前必须先备份数据库。

## Android APP

Android 工程在 `mobile-app/`。签名文件不进入仓库，需要从安全备份恢复到 `mobile-app/signing/`。


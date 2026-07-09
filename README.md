# Xinyuan Gas Hazard Inspection

淮北鑫远燃气隐患排查系统，包含手机 APP、手机 H5、电脑浏览器后台、PHP API 后端和 MySQL 数据库迁移脚本。

## 目录结构

```text
mobile-app/         Android 手机 APP 源码
backend/            PHP API 后端
frontend/admin/     电脑浏览器后台
frontend/mobile/    手机 H5 页面
shared/             前后端共用目录/字段定义
database/patches/   数据库迁移脚本
tools/              数据导入、分析、远程维护辅助脚本
docs/               部署、备份、换电脑恢复说明
```

## 不进入仓库的内容

- 云服务器密码、SSH 私钥、数据库密码、`.env`
- Android 签名文件、`local.properties`
- 用户真实 Excel、照片、数据库备份
- APK、截图、临时补丁包、Codex 输出目录

这些内容需要放在安全备份或密码管理器中。

## 快速开始

1. 复制 `backend/.env.example` 为 `backend/.env`，填入数据库配置。
2. 后端入口为 `backend/public/index.php`。
3. 后台页面目录为 `frontend/admin`。
4. 手机 H5 页面目录为 `frontend/mobile`。
5. Android 工程在 `mobile-app`，用 Android Studio 打开即可。

更多说明见：

- [部署说明](docs/DEPLOYMENT.md)
- [换电脑恢复](docs/RESTORE_ON_NEW_PC.md)
- [备份与敏感信息](docs/BACKUP_AND_SECRETS.md)


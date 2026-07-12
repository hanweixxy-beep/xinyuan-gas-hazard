# New PC Handover Checklist

## GitHub 已保存

- Android APP 全部源码和 Gradle Wrapper
- PHP 后端源码
- 手机 H5 和电脑后台源码
- 完整 MySQL 表结构及增量迁移脚本
- Nginx 配置模板
- 数据导入、备份和部署辅助脚本
- 项目部署、恢复和安全说明

GitHub 仓库：

```text
https://github.com/hanweixxy-beep/xinyuan-gas-hazard-APP-H5-PHP-MySQL-.git
```

## 不能上传 GitHub，必须单独安全转移

1. 云服务器 SSH 私钥：`work/ssh/xinyuan_gashazard_deploy`
2. Android 发布签名：
   - `release-keystore.properties`
   - `xinyuan-release.jks`
3. 生产服务器的 `backend/.env`
4. 如需离线留档：数据库备份和上传照片备份

这些文件包含访问权限或用户数据，应放入加密 U 盘、加密压缩包或密码管理器，不能提交到 GitHub。

## 新电脑验收

```powershell
git clone https://github.com/hanweixxy-beep/xinyuan-gas-hazard-APP-H5-PHP-MySQL-.git
cd xinyuan-gas-hazard-APP-H5-PHP-MySQL-
git status
git tag --list
```

随后用 Android Studio 打开 `mobile-app`，恢复签名文件和 SSH 私钥，即可继续开发和部署。

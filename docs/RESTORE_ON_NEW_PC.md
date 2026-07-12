# Restore On A New PC

新电脑继续开发的最快流程：

1. 安装 Git、Android Studio、JDK 17、PHP 8.3、MySQL 客户端。
2. 从 GitHub 私有仓库克隆项目。
3. 从安全备份恢复以下文件：
   - `backend/.env`
   - 服务器 SSH 私钥
   - `mobile-app/signing/release-keystore.properties`
   - `mobile-app/signing/xinyuan-release.jks`
   - 必要的数据库备份和上传照片备份
4. 用 Android Studio 打开 `mobile-app/`。
5. 在本地或测试服务器配置 `backend/.env`。
6. Android Studio 会在新电脑自动生成 `mobile-app/local.properties`，不需要从旧电脑复制。
7. 验证：

```bash
php -l backend/public/index.php
cd mobile-app
./gradlew assembleDebug
```

8. 连接手机后安装测试 APK，或直接访问手机 H5。

完整换机交接清单见 `docs/NEW_PC_HANDOVER.md`。

## GitHub 私有仓库策略

- `main` 保存稳定代码。
- 每次大功能完成后打标签，例如 `v1.4.0-admin-filter-h5-ui`。
- 不把真实用户数据、照片、密码、私钥提交到 Git。

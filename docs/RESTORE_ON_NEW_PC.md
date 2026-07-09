# Restore On A New PC

新电脑继续开发的最快流程：

1. 安装 Git、Android Studio、JDK、PHP、MySQL 客户端。
2. 从 GitHub 私有仓库克隆项目。
3. 从安全备份恢复以下文件：
   - `backend/.env`
   - 服务器 SSH 私钥
   - Android 签名文件
   - 必要的数据库备份和上传照片备份
4. 用 Android Studio 打开 `mobile-app/`。
5. 在本地或测试服务器配置 `backend/.env`。
6. 验证：

```bash
php -l backend/public/index.php
```

7. 连接手机后安装测试 APK，或直接访问手机 H5。

## GitHub 私有仓库策略

- `main` 保存稳定代码。
- 每次大功能完成后打标签，例如 `v1.4.0-admin-filter-h5-ui`。
- 不把真实用户数据、照片、密码、私钥提交到 Git。


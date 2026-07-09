# Backup And Secrets

## 必须安全保存的内容

- 云服务器登录方式和 SSH 私钥
- 数据库账号密码
- Android release 签名文件和签名密码
- `backend/.env`
- MySQL 数据库备份
- `/var/www/xinyuan-gas/uploads` 上传照片

## 推荐备份频率

- 每次部署前：备份后端、前端、数据库。
- 每天：备份 MySQL 数据库。
- 每周：备份上传照片目录。
- 每次发布 APP：保存 APK、版本号、SHA256、对应 Git 标签。

## 不建议放入 GitHub 的内容

- 用户电话、地址、照片
- 导出的 Excel
- 服务器密码截图
- APK 构建产物
- SSH known_hosts 和私钥

GitHub 私有仓库只保存源码、说明文档、数据库结构迁移脚本和不含密码的配置模板。


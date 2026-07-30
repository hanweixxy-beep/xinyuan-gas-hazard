# 生产环境自动备份

## 目标

鑫远燃气项目采用以下备份策略：

- 每天备份 MySQL 数据库。
- 每天为上传照片生成增量快照。未变化的照片使用硬链接，不重复占用磁盘。
- 每周生成一个包含数据库、全部照片和关键服务器配置的完整压缩包。
- 每次备份后校验压缩包、校验和与文件数量。
- 本机保留最近 14 个每日快照、最近 8 个每周完整备份。
- 使用加密的 `rclone crypt` 远端后，可每天将数据库、照片变化和每周完整包同步到异地对象存储。

源码以 GitHub 私有仓库为准，生产数据和照片绝不提交到 GitHub。

## 服务器目录

```text
/etc/xinyuan-gas/backup.env       # 备份配置，root 可读
/etc/xinyuan-gas/rclone.conf      # 异地存储配置，启用后 root 可读
/usr/local/sbin/xinyuan-gas-backup
/usr/local/sbin/xinyuan-gas-offsite-backup
/var/backups/xinyuan-gas/
  daily/                           # 数据库 + 照片增量快照
  weekly/                          # 每周完整归档
  state/                           # 最近成功时间和文件位置
/var/log/xinyuan-gas-backup.log
```

这些目录不在网站根目录内，Nginx 无法下载其中的文件。

## 定时任务

服务器使用北京时间 `Asia/Shanghai`：

- 每日备份：每天 `02:10`，最多随机延迟 10 分钟。
- 每周完整备份：每周日 `03:30`，最多随机延迟 15 分钟。
- 异地加密同步：配置完成后每天 `04:10`。

`Persistent=true` 表示服务器在计划时间关机，恢复运行后会补做错过的任务。

## 安装或重装

在服务器上的项目仓库根目录执行：

```bash
sudo ./tools/install-production-backup.sh
```

安装脚本会保留已有的 `/etc/xinyuan-gas/backup.env`，启用每日和每周定时器，
但不会擅自启用异地同步。

## 常用检查命令

```bash
sudo systemctl list-timers 'xinyuan-gas-backup-*'
sudo systemctl status xinyuan-gas-backup-daily.service
sudo /usr/local/sbin/xinyuan-gas-backup status
sudo tail -n 100 /var/log/xinyuan-gas-backup.log
```

手动运行及校验：

```bash
sudo /usr/local/sbin/xinyuan-gas-backup daily
sudo /usr/local/sbin/xinyuan-gas-backup verify-daily
sudo /usr/local/sbin/xinyuan-gas-backup restore-check
sudo /usr/local/sbin/xinyuan-gas-backup weekly
sudo /usr/local/sbin/xinyuan-gas-backup verify-weekly
```

`restore-check` 会创建临时数据库、导入最近一次备份、统计表和记录数，然后删除临时数据库。它不会修改生产数据库。

## 灾难恢复顺序

1. 从 GitHub 拉取源码，并按 `docs/RESTORE_ON_NEW_PC.md` 准备开发环境。
2. 在新服务器安装 Nginx、PHP 和 MySQL。
3. 从异地存储下载最新每周完整包并校验 SHA256。
4. 解压 `server-config/backend.env` 和鑫远 Nginx 配置。
5. 导入 `database.sql.gz`。
6. 恢复 `uploads/` 到 `backend/storage/uploads/`。
7. 再应用最新的每日数据库备份和照片增量历史。
8. 检查文件权限、运行 API 健康检查并登录 APP/H5/管理后台验收。

正式恢复前应先保留当前数据库副本，恢复操作必须在维护窗口执行。

## 异地对象存储

异地同步默认不启用，因为服务器当前没有对象存储桶、访问凭据或实例角色。

建议使用独立的腾讯云 COS 私有存储桶，并通过 `rclone crypt` 加密后上传。加密密码必须另存于企业密码管理器或离线介质，不能只保存在这台服务器上。

配置完成后的关键项：

```bash
OFFSITE_REMOTE=xinyuan-crypt:xinyuan-gas-production
OFFSITE_ENCRYPTED=true
RCLONE_CONFIG=/etc/xinyuan-gas/rclone.conf
```

只有明确设置 `OFFSITE_ENCRYPTED=true`，脚本才允许上传。完成一次人工上传、下载和解密恢复演练后，方可启用：

```bash
sudo systemctl enable --now xinyuan-gas-backup-offsite.timer
```

## 安全边界

- 备份目录、日志和配置均为 `root` 私有权限。
- 每周完整包含 `backend/.env`，必须视为敏感数据。
- 不通过 GitHub、公开网盘或聊天工具传输备份。
- 不把 COS SecretId、SecretKey、`rclone crypt` 密码写入 Git。
- 至少每季度进行一次从异地副本恢复到临时环境的演练。

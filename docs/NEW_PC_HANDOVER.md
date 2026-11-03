# 新电脑接手清单

以 [双机工作流程](CROSS_DEVICE_WORKFLOW.md)、[开发环境验收](DEVELOPMENT_ENVIRONMENT.md) 和 [当前接续状态](PROJECT_STATE.md) 为准。

仓库沿用：https://github.com/hanweixxy-beep/xinyuan-gas-hazard-APP-H5-PHP-MySQL-.git 。首次推送没有完成前，克隆只能拿到旧的已提交内容；“电脑上已有新版 APK”不表示 GitHub 有新版源码。

开始源码开发需要 GitHub 访问与本机工具；正式签名、服务器 SSH 密钥、生产 .env 不在 Git 中。根据实际用途从用户安全备份单独配置，不在聊天发送密码，也不照抄另一台机器的绝对路径。

先在新电脑验收同一提交号、构建、测试和隔离环境，再继续功能开发。Windows 原环境保留到 Mac 验收完成；用户真实数据、照片、签名和服务器配置备份独立管理。

生产恢复说明仍见 RESTORE_ON_NEW_PC.md、BACKUP_AND_SECRETS.md；日常 Mac 开发不需要先恢复生产数据库或生产照片。

# Xinyuan Gas Hazard Inspection

淮北鑫远燃气隐患排查系统，包含手机 APP、手机 H5、电脑浏览器后台、PHP API 后端和 MySQL 数据库迁移脚本。

当前 Android 工作版本为 `2.3.4`：登录后工作、检查记录实时保存到云端，新增检查采用单页连续表单，支持在待整改记录中
填写整改情况、拍摄多张整改照片并提交闭环，同时支持在登录、重新打开 APP 或网络恢复后，
将旧版手机本地记录自动续传到云端。总部和公司管理员可登录 APP；所有已登录的公司账号
均可查看本公司全部检查记录、协助整改待整改隐患，保留原录入人并记录实际整改人。

`2.3.0` 起，片区和门店属于检查记录的必选信息，账号只分配公司和使用权限。
后台先维护片区及其下属门店，检查人员在 APP/H5 首次登记时选择，后续登记沿用同一账号上次选择，
跨片区或门店时可随时点击“更换”。新检查提交时必须选齐两项；历史记录和旧版数据导入保留原样。

`2.0.9` 起，门头拍照会主动获取新位置，并拒绝超过 30 秒或精度差于 100 米的定位结果；
同一条记录的后续照片复用门头定位，并将定位时间和精度一同上传到服务器。

`2.0.10` 起，同一账号在 APP/H5 端和电脑后台端各只保留一个有效登录；同端新设备登录会
使旧设备退出，但同一人的手机端与电脑后台可以同时在线。

## Windows / Mac 开发交接

先读 [当前接续状态](docs/PROJECT_STATE.md)、[双机工作流程](docs/CROSS_DEVICE_WORKFLOW.md) 和 [开发环境验收](docs/DEVELOPMENT_ENVIRONMENT.md)。当前版本新增提交确认和记录列表回到顶部；源码是否已同步以真实 Git 提交和远端核对为准。

## 目录结构

```text
mobile-app/         Android 手机 APP 源码
backend/            PHP API 后端
frontend/admin/     电脑浏览器后台
frontend/mobile/    手机 H5 页面
shared/             前后端共用目录/字段定义
database/patches/   数据库迁移脚本
database/schema.sql 完整 MySQL 基础表结构
deploy/             Nginx 等服务器配置模板
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

1. 复制 `backend/.env.example` 为 `backend/.env`，填入数据库配置和服务器端腾讯地图 Web Service Key。Key 只保存在服务器 `.env`，不得写入 H5、APP 或 GitHub。
2. 后端入口为 `backend/public/index.php`。
3. 后台页面目录为 `frontend/admin`。
4. 手机 H5 页面目录为 `frontend/mobile`。
5. Android 工程在 `mobile-app`，用 Android Studio 打开即可。

H5 在门头照片阶段获取一次高精度坐标并解析用户地址；同一条记录的后续照片统一复用该地址和坐标，不重复消耗逆地址解析额度。

更多说明见：

- [部署说明](docs/DEPLOYMENT.md)
- [换电脑恢复](docs/RESTORE_ON_NEW_PC.md)
- [换机交接清单](docs/NEW_PC_HANDOVER.md)
- [备份与敏感信息](docs/BACKUP_AND_SECRETS.md)
- [生产环境自动备份与恢复](docs/PRODUCTION_BACKUP.md)
- [APP 2.0 云端化说明](docs/APP_CLOUD_FIRST_V2.md)
- [APP 2.0.9 发布与权限说明](docs/RELEASE_2.0.9_20260827.md)
- [APP 2.0.10 单端单设备登录说明](docs/RELEASE_2.0.10_20260827.md)
- [APP 2.1.0 单页登记与片区门店组织说明](docs/RELEASE_2.1.0_20260902.md)
- [APP 2.1.1 拍照提示与单次定位修复](docs/RELEASE_2.1.1_20260903.md)
- [APP 2.2.0 检查归属按需变更说明](docs/RELEASE_2.2.0_20260903.md)
- [APP 2.3.0 检查片区门店独立选择说明](docs/RELEASE_2.3.0_20260908.md)
- [后台片区与门店编辑说明](docs/ADMIN_ORGANIZATION_EDIT_20260908.md)

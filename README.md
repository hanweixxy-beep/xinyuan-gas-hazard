# Xinyuan Gas Hazard Inspection

淮北鑫远燃气隐患排查系统，包含手机 APP、手机 H5、电脑浏览器后台、PHP API 后端和 MySQL 数据库迁移脚本。

项目采用 MIT 许可，欢迎下载、Fork、修改和继续开发。再分发时请保留版权与许可声明。第三方组件沿用各自许可。 [查看 MIT 许可](LICENSE)

当前 Android 工作版本为 `2.3.8`：登录后工作、检查记录实时保存到云端，新增检查采用单页连续表单，支持在待整改记录中
填写整改情况、拍摄多张整改照片并提交闭环，同时支持在登录、重新打开 APP 或网络恢复后，
将旧版手机本地记录自动续传到云端。总部和公司管理员可登录 APP；所有已登录的公司账号
均可查看本公司全部检查记录、协助整改待整改隐患，保留原录入人并记录实际整改人。

Android 2.3.8 和手机 H5 支持配送工、检查人、片区、门店及「仅看本账号录入」组合筛选。本账号筛选按实际录入账号识别，待整改和已完成数量随账号范围更新。管理后台支持完整分页、隐患和气源排查筛选、异步 Excel 导出，以及带照片和照片链接两种导出方式。

`2.3.0` 起，片区和门店属于检查记录的必选信息，账号只分配公司和使用权限。
后台先维护片区及其下属门店，检查人员在 APP/H5 首次登记时选择，后续登记沿用同一账号上次选择，
跨片区或门店时可随时点击“更换”。新检查提交时必须选齐两项；历史记录和旧版数据导入保留原样。

`2.0.9` 起，门头拍照会主动获取新位置，并拒绝超过 30 秒或精度差于 100 米的定位结果；
同一条记录的后续照片复用门头定位，并将定位时间和精度一同上传到服务器。

`2.0.10` 起，同一账号在 APP/H5 端和电脑后台端各只保留一个有效登录；同端新设备登录会
使旧设备退出，但同一人的手机端与电脑后台可以同时在线。

## Windows / Mac 开发交接

先读 [当前接续状态](docs/PROJECT_STATE.md)、[双机工作流程](docs/CROSS_DEVICE_WORKFLOW.md) 和 [开发环境验收](docs/DEVELOPMENT_ENVIRONMENT.md)。当前版本包含提交确认、记录列表回到顶部和本账号筛选。公开前已从 Git 历史移除旧测试账号脚本，历史提交编号有所调整，旧克隆请保存本地改动后重新克隆。

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

准备 Git、PHP 8.3、MySQL 8、JDK 17 和 Android SDK 35。PHP 需要 PDO MySQL、mbstring、fileinfo、zip、gd，EXIF 扩展用于照片方向处理。

```sh
git clone https://github.com/hanweixxy-beep/xinyuan-gas-hazard-APP-H5-PHP-MySQL-.git
cd xinyuan-gas-hazard-APP-H5-PHP-MySQL-
```

1. 在本机创建空的开发数据库，例如 `xinyuan_test_dev`，将 `database/schema.sql` 导入这个空库。该文件包含重建表结构的语句，导入前核对目标数据库。
2. 复制 `backend/development.env.example` 为 `backend/.env`，填写本机数据库连接、上传与导出目录的绝对路径，以及自己的腾讯地图 Web Service Key。
3. 按下文初始化一个本机管理员。登录后台后，可以创建公司、片区、门店和检查账号。
4. 后端入口为 `backend/public/index.php`。配置 Nginx 将 `/api/` 指向后端，`/admin/`、`/mobile/`、`/shared/`、`/uploads/` 指向对应目录，参考 `deploy/nginx/xinyuan-gas.conf.example`。
5. Android Studio 打开 `mobile-app`。将 `CloudSyncClient.kt` 的 `DEFAULT_BASE_URL` 改为自己的 HTTPS API 地址，生成本机 `local.properties` 后构建 debug。浏览器页面默认访问同源的 `/api`。

### 初始化开发管理员

在终端运行下面的 PHP 命令，输入自己设置的密码，复制输出的哈希。

```sh
php -r 'echo "Admin password: "; $p = trim(fgets(STDIN)); echo password_hash($p, PASSWORD_DEFAULT), PHP_EOL;'
```

在刚创建的开发数据库执行下面的 SQL，将哈希占位文字替换为上一步的输出。

```sql
INSERT INTO companies (name, code) VALUES ('开发演示公司', 'DEV');
SET @dev_company_id = LAST_INSERT_ID();
INSERT INTO users (company_id, name, phone, password_hash, role, login_scope, can_view_company_records, status)
VALUES (@dev_company_id, '开发管理员', '19900000001', 'REPLACE_WITH_YOUR_GENERATED_HASH', 'hq_admin', 'both', 1, 'active');
```

使用这个手机号和自己设置的密码登录本机 `/admin/` 或 `/mobile/`。

### 构建与检查

Android 使用仓库中的 Gradle Wrapper 8.9。在 `mobile-app` 目录运行。

```powershell
# Windows
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

```sh
# macOS / Linux
bash gradlew :app:testDebugUnitTest :app:assembleDebug
```

正式安装包需要配置自己的 `mobile-app/signing/release-keystore.properties`。已有应用的覆盖升级需要匹配原签名。开发者可使用自己的 debug 或 release 签名构建独立部署版本。

无需数据库的后端检查可在仓库根目录运行。

```sh
php -l backend/public/index.php
php backend/tests/UserAccessTest.php
php backend/tests/ExportOrganizationColumnsTest.php
```

数据库集成测试使用独立的 `xinyuan_test_*` 数据库。部分旧测试使用 Linux MySQL socket，Windows / macOS 需适配连接设置。浏览器测试需要 Node.js、Playwright 和 Chrome，部分导出测试还需要 xlsx，详见开发环境说明。

### 参与开发

Fork 仓库后创建功能分支，完成相关检查并提交 Pull Request，描述使用场景、修改内容和验证结果。私有配置、签名、真实业务数据和生成文件请按 `.gitignore` 保存在仓库之外。

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

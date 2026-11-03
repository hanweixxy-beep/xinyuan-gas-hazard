# 两台电脑的开发环境与验收

## 固定的项目工具

| 内容 | 项目要求 | 本次验证边界 |
| --- | --- | --- |
| Android | JDK 17、Android SDK 35、Gradle Wrapper 8.9、AGP 8.7.3、Kotlin 2.0.21 | Windows 已成功；Mac 待实际执行 |
| 后端 | PHP 8.3；PDO MySQL、zip、gd（EXIF 方向处理按扩展可用性） | 不在本次交接中安装或更改生产环境 |
| 数据库 | MySQL，独立 xinyuan_test_* 开发/测试库 | 不连接生产库做开发测试 |
| 辅助工具 | Git；Node.js 20+ 运行只读交接核对脚本 | 不使用全局依赖假装项目已可复现 |

安装渠道由电脑使用者选择；不自动运行下载的安装脚本，不设置全局 Git 行为，不改系统电源或账号设置。

## Mac 的 Android 开工步骤

1. 安装 Android Studio，通过 SDK Manager 配置 SDK 35；确保 Gradle 使用 JDK 17（java -version 应显示 17）。
2. 在 Android Studio 打开 mobile-app。由本机生成被 Git 忽略的 local.properties，sdk.dir 使用 Mac 的 SDK 路径，不能复制 Windows local.properties。
3. 在 mobile-app 内运行：

~~~sh
bash gradlew --version
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
~~~

仓库原始 gradlew 的 Git 执行位为 100644，所以统一用 bash gradlew，不要求先修改执行位。首次运行可能需要下载项目声明的依赖；成功生成 APK 仍不等于已安装/运行。

正式 release 需要原签名。签名文件通过用户自己的安全渠道转移；Mac 单独生成 release-keystore.properties，用自己的 storeFile 路径。未恢复原签名时先测试 debug，不能拿新签名替代正式签名去覆盖现有应用。官方与调试包默认包名相同，手机上已有正式应用时，不要为了装 debug 卸载正式应用；使用独立测试设备或专门的隔离测试包方案。

## 本地 PHP 与数据库

1. 从 backend/development.env.example 复制为 backend/.env（已存在则先检查，不覆盖）。填写本机数据库账号、密码及两处本机绝对目录。不要把模板的占位路径当成可用配置。
2. 数据库只用 xinyuan_test_*，独立建库和独立账号；账号只授权该测试库。首次可用 database/schema.sql 还原空结构。不要向正式库导入，不对已有库盲跑完整 schema；增量结构需先核对已应用记录。
3. APP_ENV=local；DB_HOST 使用 localhost/127.0.0.1。UPLOAD_ROOT 与 EXPORT_ROOT 指向本机可写目录。地图功能需要独立测试 Key；未配置 Key 时不把定位链路标为通过。
4. 本机基础检查：

~~~sh
php -v
php -m
php -l backend/public/index.php
php backend/tests/UserAccessTest.php
php backend/tests/ExportOrganizationColumnsTest.php
~~~

最后一项会在本机生成并检查测试工作簿，需要 zip 扩展；不读取生产数据库。浏览器/H5/API 联调还需单独的本机路由与测试账号，首次阶段不自动启动服务或导入真实资料。

## 既有测试的兼容限制

- 若干后端集成测试写死 Linux MySQL socket /var/run/mysqld/mysqld.sock 和测试库命名规则。Mac 不能直接照跑并声称通过；需要后续统一测试连接配置，或在隔离 Linux 测试环境执行。现有 verify/deploy 脚本含服务器路径或建删测试库操作，不是 Mac 安装命令。
- 浏览器模拟测试工具依赖 playwright，个别还依赖 xlsx，部分指定 Windows Chrome。当前仓库未纳入统一的 Node 依赖清单及跨平台浏览器设置；这些测试仍可在已配置的 Windows 执行，Mac 尚未可复现。首次同步不自动安装插件、建立 CI 或部署新服务。
- tools/download_export_capacity.cjs、若干历史部署/排查工具含 Windows 或生产服务器专用路径；保留其用途，不将其当通用开发入口。

## Mac 接手验收记录

- [ ] 私有仓库克隆成功，本机 HEAD 与 Windows 交接提交一致，工作区干净。
- [ ] JDK 17、SDK 35 与 Wrapper 版本正确；debug 测试、构建与 lint 通过。
- [ ] PHP 扩展与两项无需数据库的检查通过。
- [ ] 本地 .env 为开发配置；测试库和目录独立，不带生产资料。
- [ ] 如需正式打包，原签名证书比对成功；如需服务器维护，再单独授权并配置密钥。
- [ ] 手机与浏览器实际功能验收有证据；未执行项明确标注，不把 Windows 结果写为 Mac 实测。

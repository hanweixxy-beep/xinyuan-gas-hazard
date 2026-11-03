# 现有 GitHub 仓库与首次同步

已存在私有仓库，继续沿用，不需要新建空仓库：

~~~text
https://github.com/hanweixxy-beep/xinyuan-gas-hazard-APP-H5-PHP-MySQL-.git
~~~

当前准备时 main 为 87af737，工作源码为 Android 2.3.3；累计工作尚需批准后提交、推送。不能只点击推送就上传未提交文件。

首次同步顺序：仓库外源码与 Git 历史备份 → 审核差异和敏感信息 → 批准具体提交范围 → 形成提交 → 确认后推送现有 origin/main → 比较本机完整提交号与 git ls-remote --heads origin main → Mac 克隆并验收。

推送前如远端已变更，停下来比较两边，不强推。不要把 .env、签名、客户照片、导出表和 APK 放进提交。不对现有代码执行全仓库行尾重整或覆盖复原。

日常操作见 [双机流程](CROSS_DEVICE_WORKFLOW.md)，当前状态见 [PROJECT_STATE.md](PROJECT_STATE.md)。

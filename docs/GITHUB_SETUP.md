# GitHub Private Repository Setup

当前本地仓库已经整理完成，并已创建第一次提交和版本标签。

## 推荐仓库名

```text
xinyuan-gas-hazard
```

仓库必须设为 Private。

## 方式一：使用 GitHub 网页

1. 登录 GitHub。
2. 新建一个 Private 仓库，名称建议 `xinyuan-gas-hazard`。
3. 不要勾选自动创建 README、`.gitignore` 或 License。
4. 创建后复制仓库 HTTPS 地址，例如：

```text
https://github.com/<your-account>/xinyuan-gas-hazard.git
```

5. 回到 Codex，把这个地址发给我，我会添加 remote 并推送。

## 方式二：安装 GitHub CLI

安装 GitHub CLI 后运行：

```powershell
gh auth login
gh repo create xinyuan-gas-hazard --private --source . --remote origin --push
```

如果仓库已经在网页上创建：

```powershell
git remote add origin https://github.com/<your-account>/xinyuan-gas-hazard.git
git push -u origin main
git push origin v1.4.0-cloud-admin-h5-app-snapshot
```

## 当前本地状态

- 分支：`main`
- 首次提交：`Initial xinyuan gas hazard system source`
- 标签：`v1.4.0-cloud-admin-h5-app-snapshot`


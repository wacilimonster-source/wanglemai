# CI 发版说明

发版入口：GitHub Actions → **Release** → Run workflow（`.github/workflows/release.yml`）。日常 push/PR 由 `.github/workflows/ci.yml` 构建并跑 JVM 单测。

发版采用手动触发而不是 tag 触发，原因是 jsDelivr 镜像使用 `@vX.Y.Z` 路径读取仓库内的 APK：必须先构建出 APK、提交到 `releases/`，再打 tag，tag 才能包含可下载的 APK。CI 统一完成这几步。

CI 只负责 JVM 单测、`assembleDebug` 和产物发布；设备 instrumentation、真机验收仍按 [验收文档](03-acceptance.md) 在本地完成并记录，CI 不改变发布门槛。

## 一次性配置

### 1. 签名密钥 Secret

现有 0.1.0–0.3.5 均使用本机 `~/.android/debug.keystore` 签名（证书 SHA-256 `e436a586cde571929d012c964d02a1cca45632abcdb662745b734ddfdb274f06`）。CI 必须使用同一把 key，否则新 APK 无法覆盖安装旧版。

```bash
# Git Bash；需要仓库 admin 权限
base64 -w0 ~/.android/debug.keystore | gh secret set ANDROID_DEBUG_KEYSTORE_BASE64
```

```powershell
# PowerShell 等价写法
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\.android\debug.keystore")) |
  gh secret set ANDROID_DEBUG_KEYSTORE_BASE64
```

也可以在 GitHub 仓库 Settings → Secrets and variables → Actions 新建 Secret `ANDROID_DEBUG_KEYSTORE_BASE64`，值填 base64 文本。不要把 keystore 提交进仓库。

### 2. Workflow 写权限

Settings → Actions → General → Workflow permissions 选择 **Read and write permissions**。release workflow 需要 `contents: write` 来提交 `releases/`、`update/latest.json` 并打 tag。

### 3. 分支保护

`main` 不能阻止 GitHub Actions 直接 push。如果启用了 required PR / required status checks，需要给 Actions 放行（bypass），否则 workflow 会在 `git push` 步骤失败。

## 发版流程

1. 更新 README 的“当前交付 / 本版改进”、`docs/development-log.md` 和 `docs/release-notes-X.Y.Z.md`（release notes 可选；存在时作为 Release 正文），并把文档提交到 `main`。
2. Actions → Release → Run workflow：
   - `version`：`0.4.0` 或 `v0.4.0`，必填。
   - `release_name`：可留空，默认 `忘了没 vX.Y.Z MVP`。
   - `dry_run`：首次或改 workflow 后先勾选。dry_run 会构建、跑单测、校验证书、生成临时清单，然后上传 APK artifact 停止；不提交、不打 tag、不发 Release。
3. dry_run 通过后再正式运行一次。

正式运行会：

- 用 `scripts/bump-version.py` 把 `versionCode` 加 1、`versionName` 设为 `X.Y.Z-mvp`；
- 运行 `:app:testDebugUnitTest :app:assembleDebug`；
- 用 `apksigner` 校验 APK 证书指纹，不匹配立即失败；
- 写入 `releases/wanglemai-X.Y.Z-debug.apk`，重算 `releases/SHA256SUMS.txt`；
- 用 `scripts/update-manifest.py` 生成 `update/latest.json`；
- 提交 `release: vX.Y.Z` 到 `main`，创建并推送 annotated tag `vX.Y.Z`；
- 用 `gh release create` 创建 Release，上传 APK 和 `SHA256SUMS.txt`；
- 执行 `scripts/purge-jsdelivr-manifest.sh` 清理 `@main` 清单缓存。

## 失败重跑

- **失败在提交之前**（测试、构建、签名、dry_run）：直接重新运行即可。
- **失败在提交之后、tag 推送之前**：重跑会以相同版本号重新构建并提交（`versionCode` 不再递增），随后补推 tag 和 Release。
- **失败在 tag 推送之后、Release 之前**：tag 已指向 main HEAD。重新运行同一版本会进入续跑模式，跳过构建/提交，直接用仓库内已提交的 APK 创建或补传 Release。
- **tag 已存在但不指向 main HEAD**：workflow 会拒绝移动已发布 tag。需要人工确认：要么删除错误 tag/Release 后重跑，要么换新版本号。
- Release 已存在时使用 `gh release upload --clobber` 覆盖资产，不会重复创建。

## 验证清单

- Actions run 全绿，`dry_run` 时没有新 commit。
- `main` 上有 `release: vX.Y.Z` 提交，包含版本号、APK、`SHA256SUMS.txt`、`update/latest.json`。
- Release 资产包含 APK 和 `SHA256SUMS.txt`；Release 标题与 `release_name` 一致。
- `update/latest.json` 的 `sha256` 与 Release APK 一致。
- `https://cdn.jsdelivr.net/gh/wacilimonster-source/wanglemai@vX.Y.Z/releases/wanglemai-X.Y.Z-debug.apk` 可下载。
- 新 APK 能覆盖安装上一版，说明签名连续。
- jsDelivr 的 `@main/update/latest.json` 已是新版本；若仍是旧值，等待缓存或手动执行 `bash scripts/purge-jsdelivr-manifest.sh`。

## 常见问题

| 现象 | 处理 |
|---|---|
| `Missing repository secret ANDROID_DEBUG_KEYSTORE_BASE64` | 按“一次性配置”设置 Secret。 |
| `git push` 被拒绝（protected branch / 403） | 检查 Workflow permissions 是否为 Read and write，以及分支规则是否允许 Actions 推送。 |
| APK 证书指纹不匹配 | 恢复的 keystore 不是历次发版用的那把；不要强行发布，否则老用户无法覆盖升级。 |
| `already at X.Y.Z; pass --allow-same` | 目标版本与当前 `versionName` 相同；确认是续跑还是版本号填错。 |
| jsDelivr 清单仍是旧版本 | purge 步骤失败会标黄但不阻断发版；本地执行 purge 脚本或等待缓存过期。 |
| 版本号写错但已发 tag | tag 不改写；删除错误 Release 与 tag 后，用新版本号重新发布。 |

## 安全边界

仓库公开，Secret 中的 debug keystore 私钥不会随仓库分发；但它本质仍是 debug 签名，不应作为安全更新身份。需要正式签名时应另建 release keystore，并接受一次“卸载重装”或迁移签名方案的成本。

# Android 开发与交接记录

## v0.3.1 · 桌面小部件精修 / 2026-11-07

支持两排高度，统一间距与48dp触区，紧凑档将底栏空间用于更多事项。桌面和配置预览复用RemoteViews与尺寸预算，适配窄宽、大字号、多尺寸和撤销状态。玻璃材质细化边缘高光，浅色按对比度校核采用92%不透明度，深色90%。

修复MainActivity清单打开请求被过早清除的协程取消竞态。完整回归中的新增编辑用例改用独立清单，字面搜索用例同步查询结果并刷新Android自动化缓存。

升版为0.3.1-mvp，versionCode为6，沿用既有签名。58/58项JVM测试、34/34项API34设备测试通过，Lint为0错误、55项警告。构建、设备日志和摘要位于 `docs/evidence/*0.3.1*`。目标HyperOS真机占格、壁纸合成和桌面重启恢复继续列为待验项。


## 未发版：暖米色视觉改造 / 2026-11-07

按 `docs/visual-refresh/development.md`（提案 v1）把应用与 Widget 从冷绿主题改为「奶油米 + 暖纸白 + 焦糖棕」，业务逻辑（repository、ViewModel、更新、备份、草稿、批量录入）未动，全部文案锚点保留（FrameworkUiTest 的文本/描述定位逐字未改）。

### 变更

- 主题基线（任务 1）：`Theme.kt` 换浅/深两套暖色 scheme（secondary/tertiary 映射 primary、container 映射 surfaceVariant、`surfaceContainer*` 显式暖阶），新增 Typography（页面标题 26/36、面板标题 22/32、正文 16/24、辅助 12/18、按钮 14/20、导航 12/16）与 Shapes（分段 11 / 搜索 14 / 按钮 15 / 列表组 20 / 面板 28）；`values{,-night}/colors.xml`、`widget_colors.xml` 换暖 token；Widget 玻璃底改为浅色奶油 92% / 深色暖棕 94% + 1dp 顶部高光 + 1dp 描边（layer-list）；启动图标换焦糖棕。
- 主清单（任务 2）：底部导航字符图标 ✓⌑⚙ 换 24dp 线性矢量（待办/待买/设置）；清单选择器浅填充；搜索浅底 14dp 圆角 + 前置搜索图标；未完成/已完成 FilterChips 合并 40dp 分段控件（selectableGroup + RadioButton 语义，选中 surface 底 + primary 字）并加“N 项待办/待买”计数；列表包进 20dp 圆角 surface 组 + 组内细分隔线；行高 68/56dp、方形 Checkbox 换 48dp 触区圆形完成控件（20dp 圆环/焦糖实心勾）、置顶改 pin 矢量；FAB 移除，改导航上方整宽「＋ 记一条」。
- 编辑/管理/设置（任务 3）：编辑面板顶部 28dp 圆角、遮罩改 28% 暖棕、输入框统一 background 填充 + outlineVariant 边线 + 聚焦 primary 2dp、主输入 20/30sp、清单选择浅填充、底栏 surfaceContainer；清单管理行进同款分组面；设置重组为「外观与桌面 / 清单与数据 / 关于忘了没」三组 18dp 圆角卡片（行 ≥56dp），检查更新/备份/pin 全部状态与文案不变。
- Widget 与配置（任务 4）：`widget_content/row/row_compact` 字号对齐 token（标题 16 Medium、正文 14、数量 12、入口 14）；row_check 字符 ○ 换 20dp 圆环矢量 ImageView（浅/深两套烘焙色，保留 id 与 48dp 触区、PendingIntent 与语义不变）；`WidgetUpdateCoordinator` 行数公式改为 floor + “剩余空间 ≥ 3/4 行高保 1 行”，真极小尺寸折叠为标题+页脚入口（渲染器区分“无记录”与“空间不足”文案）；配置页顶部新增随主题/密度即时切换的材质预览。

### 过程中修复的既有缺陷

- `ApkDownloader` 双源回退循环不检查协程取消：被取消的旧任务会继续走 fallback 并清理目录，与替代它的新下载竞态（可能删除新任务刚写好的安装包）。已在回退处补 `ensureActive()`。

### 实际结果

| 检查 | 结果 | 证据 |
|---|---|---|
| 原型校验 | verify.cjs 27 项通过；npm test 30 项通过 | prototype/warm/verify.cjs、tests/prototype.test.cjs |
| JVM 单元测试 | 58/58 通过 | ValidationTest 13、BackupCodecTest 12、UpdateCheckerTest 13、ApkDownloaderTest 14、UpdateCenterTest 6 |
| 仪器测试 | OK (29 tests) | FrameworkUiTest 13 + RepositoryTest 16（emulator-5556 / API34；一次 TopResumedActivityChange 平台级崩溃复跑通过） |
| Lint / 构建 | 0 error，`assembleDebug`/`lintDebug`/`assembleDebugAndroidTest` 通过 | gradle 输出 |
| 截图 | 11 张浅/深/130% 字号抽查 | docs/visual-refresh/native/ |

截图（docs/visual-refresh/native/）：主屏浅/深、已完成筛选、待买、编辑（键盘开）、设置、设置-检查更新、清单管理、Widget 标准+大号浅/深（debug harness）、130% 字号抽查。

### 待验项（如实记录）

- 配置页截图未捕获：`appwidget bind` 在 API34 shell 不支持、复用 AVD 残留安装成功页拦截 shell 直启，且模拟器随后被另一会话占用；配置页功能由 FrameworkUiTest 两项配置用例覆盖（断言标题/控件/保存路径，均通过），预览卡渲染已被这些用例间接验证。
- 360dp 窄屏、200% 字号、TalkBack 人工验收、API26/31/36、HyperOS 真机与多壁纸对比矩阵未执行（沿用 README 已知限制清单）。

---

## 未发版：检查更新改为客户端内下载安装 / 2026-11-06

设置页“检查更新”从“跳浏览器下载”改为客户端内直接下载安装包；版本号不变（versionCode 4 / 0.2.2-mvp），随下次发版发布。

### 变更

- 新增 `data/update/ApkDownloader.kt`：jsDelivr 国内加速优先，失败自动切 GitHub；`HttpURLConnection` 流式下载，边下边算 SHA256，完成后与清单校验值比对，不匹配即删除文件；下载前校验 URL 前缀（GitHub releases 下载 / jsDelivr 镜像，复用 `UpdateChecker` 白名单）；取消/失败清理半成品与目录内旧 APK。
- 设置页：两个浏览器下载按钮替换为“下载安装包”+ 进度条（总量未知时为不定态）+“取消下载”；完成后显示“安装”“重新下载”，并标注“已通过国内加速源下载”；保留“打开发布页”。
- 安装：`FileProvider` + `ACTION_INSTALL_PACKAGE`；未授予“安装未知应用”时先跳系统设置页并提示，授权后返回再点“安装”。
- 权限与资源：新增 `REQUEST_INSTALL_PACKAGES`（仅用于调起系统安装器，安装由用户在系统弹窗确认）和 `res/xml/file_paths.xml`。
- 测试：新增 `ApkDownloaderTest` 14 项（镜像优先、双源回退、中断回退、SHA256 不匹配删文件、进度单调、不定长、截断、URL/文件名白名单、旧 APK 清理、无源报错）。
- 文档同步：README、docs/03、docs/07 中“不自动下载/安装”的说法更新为“客户端内下载、用户确认安装”。

### 交互修订（2026-11-07，未发版）

自查发现三处交互问题并修复：

- 下载/检查状态从 `SettingsScreen` 的 `remember` 提升到应用级 `UpdateCenter`（挂 `AppContainer`，跑在应用 scope）：切页签、旋转屏幕不再丢进度，下载在后台继续，设置页只渲染 StateFlow。取消与重启下载用“仅当前任务可写状态”的约定避免旧任务覆盖新状态。
- 下载完成新增全局 Snackbar（`MainActivity` 收 `UpdateCenter.events`）：「更新包已下载并通过 SHA256 校验」带「安装」动作，在其他页签也能看到并可一键调起安装；设置页 Ready 文案补 `liveRegion`（TalkBack 可感知完成）。
- 进度回调携带当前下载源：下载中显示“正在通过国内加速源下载 / 正在从 GitHub 下载”，jsDelivr 失败切 GitHub 时显示“加速源不可用，已改用 GitHub 重新下载”，不再出现无解释的进度倒退。

新增 `UpdateCenterTest` 6 项（完成态与加速标记、中断回退标记、双源失败、取消回 Idle 且无残留、重启替换旧状态、初始空态）。

### 实际结果

| 检查 | 结果 | 证据 |
|---|---|---|
| JVM 单元测试 | 58/58 通过 | ValidationTest 13、BackupCodecTest 12、UpdateCheckerTest 13、ApkDownloaderTest 14、UpdateCenterTest 6 |
| Debug 构建 | 通过，0 warning | `:app:assembleDebug :app:testDebugUnitTest` |
| 模拟器端到端 | 通过 | 见下与截图 |

端到端（Android 14/API34 模拟器，工程临时标为 versionCode 3 / 0.2.1-mvp，验完还原）：检查更新显示“发现新版本：v0.2.2”和 SHA256；点“下载安装包”后**立刻切到待办页签**，下载在后台继续完成，待办页弹出全局 Snackbar「更新包已下载并通过 SHA256 校验」并带「安装」动作；回到设置页 Ready 状态完整保留（含“已通过国内加速源下载”标注）；点“安装”跳系统授权页，开启后返回再点“安装”，系统安装器弹出“Do you want to update this app?”，确认后覆盖安装成功，`dumpsys package` 显示 versionCode=4 / 0.2.2-mvp（即发布的 v0.2.2 APK，与 Release 同一调试签名）。

环境备注：`emulator-5554` 当时被另一会话的阅读器应用反复抢占前台，交互修订的端到端改在独立启动的 `perf_verify` AVD 上完成；该 AVD 复用残留的旧安装成功页会拦截新的安装 intent（deliver-to-top），force-stop `com.google.android.packageinstaller` 并移除任务后安装流程恢复正常——真机上用户点过 Done/Open 后不存在该残留。

注意：更新包下载期间仍只申请 INTERNET 与 REQUEST_INSTALL_PACKAGES，无后台轮询；安装始终由用户在系统弹窗确认，与 README 声明一致。

最终状态：安装完成的 0.2.2（即发布 APK）再次“检查更新”显示“已是最新版本：v0.2.2”，此时只保留“打开发布页”，无下载按钮（该安装包是旧版构建，新下载 UI 随下次发版生效）。

截图：[发现新版本与下载安装包](android-update-inapp-download-button.png)、[下载完成已校验](android-update-inapp-downloaded.png)、[系统安装器](android-update-inapp-installer.png)、[最终已是最新](android-update-inapp-latest.png)、[其他页签的全局完成通知](android-update-inapp-snackbar.png)。

---

## 当前交付：0.2.2-mvp / 2026-11-06

小部件底色从实色改为 C · 暖调半透明玻璃。

### 设计选择

- 浅色：奶油白 `rgba(249,246,233,0.76)`，1dp 白色高光描边 `rgba(255,255,255,0.72)`。
- 深色：墨绿 `rgba(17,34,25,0.80)`，1dp 浅绿描边 `rgba(165,215,185,0.30)`。
- 24dp 圆角；文字仍使用实色，保证壁纸任意变化时的对比。
- Android RemoteViews 不支持实时背景模糊，实际实现是半透明色面 + 描边，不使用虚假模糊。

### 变更与结果

- 更新 `widget_background_light.xml` / `widget_background_dark.xml`；debug WidgetHost 增加渐变测试壁纸并截图。
- 版本升为 versionCode 4 / versionName 0.2.2-mvp。
- JVM 38/38、设备 29/29、Lint 0 error。
- APK SHA256：`9aa9d892a252c7e83c5d6e6d475663b68ec0f0fb8b065c4ff5a3fb8c59e416fa`
- Release：<https://github.com/wacilimonster-source/wanglemai/releases/tag/v0.2.2>
- APK：<https://github.com/wacilimonster-source/wanglemai/releases/download/v0.2.2/wanglemai-0.2.2-debug.apk>
- 国内加速 APK：<https://cdn.jsdelivr.net/gh/wacilimonster-source/wanglemai@v0.2.2/releases/wanglemai-0.2.2-debug.apk>

发布后验证：

- 旧 `0.2.1` 安装检查更新，实际显示“发现新版本：v0.2.2”，同时出现 GitHub 下载、国内加速下载、发布页和 SHA256；国内加速入口成功进入 Chrome。
- jsDelivr `@main/update/latest.json` 分支缓存需要发布后主动 purge；执行 `scripts/purge-jsdelivr-manifest.sh` 后清单从 v0.2.1 更新为 v0.2.2。
- 最终安装 0.2.2 APK；版本 versionCode 4。

截图：[暖调玻璃 Widget](android-widget-translucent-c.png)、[C 方案设计稿](widget-design-c.png)、[可更新界面](android-update-available.png)。

### 负一屏实测反馈（2026-11-06）

用户反馈：目标澎湃 OS 3 的负一屏只能添加小米小部件，标准 Android 原生小部件找不到；原生小部件只能在普通桌面添加。由此确认当前标准 Widget 版本**不支持负一屏**，不能将普通桌面 `AppWidgetHost` 测试或普通桌面添加行为视为负一屏通过。

进一步核对小米官方《小部件提交审核与上传操作指南》：包含小部件的 APP 必须先在小米应用商店上传/更新并通过应用商店审核；使用企业开发者账号；首次接入先邮件联系，再在小部件开放平台提交审核；APP 被下架时小部件同步下架。文档未提供侧载 APK、本地调试或白名单进入负一屏的路径。

**结论：** 本项目不上架应用商店、只自有 APK 下发，不满足小米小部件准入前提；负一屏不开发，不添加 `miuiWidget`、独立 `:widgetProvider` 或专有刷新。普通桌面标准 Widget 继续保留。官方依据：<https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1588>、<https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584>。

---

## 历史：0.2.1-mvp / 2026-11-06

在 0.2.0 基础上增加更新检查的国内可达性兜底，版本升为 versionCode 3 / versionName 0.2.1-mvp。

### 本次变更

- `GitHubUpdateClient`：GitHub Releases API 优先；网络/HTTP/解析失败时回退到仓库内 `update/latest.json` 的 jsDelivr 清单。
- 更新结果包含 SHA256、GitHub APK、jsDelivr 国内加速 APK、Release 页；仍只打开浏览器，不自动下载/安装。
- jsDelivr 清单地址：`https://cdn.jsdelivr.net/gh/wacilimonster-source/wanglemai@main/update/latest.json`。
- 增加 `scripts/update-manifest.py`，发布时从本地 APK 生成版本、哈希和下载链接清单。
- 仅新增 INTERNET 权限；没有后台轮询或账户。
- 当前仍为 debug 签名；正式公开分发前应改为 release 签名。

### 实际结果

| 检查 | 结果 | 证据 |
|---|---|---|
| Debug/AndroidTest 构建 | 通过 | docs/evidence/android-build-0.2.1.txt |
| JVM 单元测试 | 38/38 通过 | ValidationTest 13、BackupCodecTest 12、UpdateCheckerTest 13 |
| 设备/Room/UI/Widget | 29/29 通过，`OK (29 tests)` | docs/evidence/android-instrumentation-0.2.1.txt |
| Android Lint | 0 error | docs/evidence/android-lint-0.2.1.txt |
| APK SHA256 | `76a2b6a971cf945ad71774e178af301978685e5ffd2c2a859543624f5a7edc1a` | releases/SHA256SUMS.txt |

`UpdateCheckerTest` 覆盖 GitHub JSON、jsDelivr 清单、SHA256、国内加速 URL、GitHub 成功不访问镜像、GitHub 失败回退、双源失败等 13 项。

### 发布信息

- 仓库：<https://github.com/wacilimonster-source/wanglemai>
- Release：<https://github.com/wacilimonster-source/wanglemai/releases/tag/v0.2.1>
- GitHub APK：<https://github.com/wacilimonster-source/wanglemai/releases/download/v0.2.1/wanglemai-0.2.1-debug.apk>
- 国内加速 APK：<https://cdn.jsdelivr.net/gh/wacilimonster-source/wanglemai@v0.2.1/releases/wanglemai-0.2.1-debug.apk>
- 静态清单：<https://cdn.jsdelivr.net/gh/wacilimonster-source/wanglemai@main/update/latest.json>

发布后验证：

- 旧 `0.2.0` APK（已发布版本）安装后检查更新，显示“发现新版本：v0.2.1”和 GitHub 下载入口。
- 用同代码、临时标记为 0.2.0 的未发布测试 APK 验证新 UI：显示“GitHub 下载”“国内加速下载”“打开发布页”和 SHA256；点国内加速下载成功进入 Chrome。
- jsDelivr 静态清单与 APK 均返回 HTTP 200；APK 哈希与 GitHub Release 资产一致。
- 最终安装 `wanglemai-0.2.1-debug.apk`，检查更新显示“已是最新版本：v0.2.1”。

[可更新界面截图](android-update-available.png)

---

## 历史：0.2.0-mvp / 2026-11-06

原生 MVP 功能已实现，并在明确选定的 Android 14/API34 模拟器上完成构建、JVM、Room、界面和 Widget 宿主测试；未在小米真机或负一屏验证。

### 实际环境

- Windows / Git Bash + PowerShell；Temurin JDK17。
- Android SDK36 编译，target 36，min 26；Gradle Wrapper 8.14.3 / AGP 8.13.1 / Kotlin 1.9.24。
- 唯一测试设备：`emulator-5558`（AVD `pika_test`，Android 14 / API34，`sdk_gphone64_x86_64`，1080x1920 override，420dpi）。
- 同时连接的 `127.0.0.1:16416`、`127.0.0.1:5557`（PGEM10）未安装、未操作。
- 未使用小米真机；用户已确认目标 HyperOS 3，机型、完整系统/Android/桌面/智能助理版本仍待提供。

### 本次变更范围

- 清单：自定义清单 CRUD、默认清单保护、同类重名校验、删除迁移记录/软删除项/草稿、重绑小部件并清撤销。
- 记录：50 条原子批量、原始行号错误、跨类型移动确认、置顶/固定排序、完成恢复。
- 草稿：Room 长期草稿、300ms debounce、onStop 补写、恢复/丢弃、编辑版本冲突、保存成功清除；epoch 防止旧写覆盖。
- 备份：SAF 导出、5MB 限制、严格校验、预览、事务合并、同名后缀、同 ID 跳过、失败回滚。
- App：自定义清单选择、管理页、设置、搜索/状态/类型记忆、浅深色主题、加载/空态/错误。
- 编辑器：底部面板样式、IME 自动聚焦、返回先收键盘、保存并继续、冲突处理、跨类型确认。
- Widget：两种尺寸、每实例清单/主题/密度、多实例独立、尺寸与字体缩放行数、显式浅深色、冲突页脚、错误/空态、主动刷新、配置取消不落库。
- 版本：versionCode 2 / versionName 0.2.0-mvp。
- 更新检查（用户新增范围）：设置页按 GitHub Releases API 手动检查；展示最新版本、发布页和 APK 下载入口，不自动下载/安装；仅新增 INTERNET 权限。

### 实际测试结果

| 检查 | 结果 | 证据 |
|---|---|---|
| Debug APK + AndroidTest APK 构建 | 通过 | docs/evidence/android-build-0.2.0.txt |
| JVM 单元测试 | 33/33 通过 | ValidationTest 13、BackupCodecTest 12、UpdateCheckerTest 8 |
| Room/UI/Widget 仪器测试 | 29/29 通过，`OK (29 tests)` | docs/evidence/android-instrumentation-0.2.0.txt |
| RepositoryTest | 16/16 | 默认清单幂等、防重、CAS、撤销、搜索、软删除、批量、清单迁移、草稿、排序、备份合并/回滚 |
| FrameworkUiTest | 13/13 | 新增编辑、校验失败保留输入、批量、草稿恢复/Back、清单管理、保存并继续、跨类型移动、冲突重载、搜索/主题、配置取消、双尺寸多实例、200% 字体、真实 AppWidgetHost |
| APK 签名 | 通过 | APK Signature Scheme v2 |
| APK 权限 | 仅 INTERNET；无存储运行时/通知/麦克风等 | INTERNET 仅用于手动检查 GitHub Releases；另有 AndroidX 包内 signature 动态 Receiver 权限 |
| Android Lint | 通过（0 error） | docs/evidence/android-lint-0.2.0.txt |

最终设备测试完整输出：`OK (29 tests)`，无跳过。JVM 与设备共 62 项。

Gradle 的 `connectedDebugAndroidTest` 在当前环境仍可能出现 “Failed to receive the UTP test results”，同时测试 XML 为 0 失败；本记录以已安装测试 APK 的 `am instrument` 输出和 XML 为准。

### 复现命令

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest

adb -s emulator-5558 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5558 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5558 shell am instrument -w -r com.wanglemai.app.test/androidx.test.runner.AndroidJUnitRunner
```

只向明确选定的 `emulator-5558` 安装；没有向其他已连接设备安装。

### 人工/设备补充验证

- **SAF 导出**：设置 → 导出备份 → DocumentsUI → Downloads，生成 `wanglemai-backup-20261106-0512.json`（1678 bytes）；JSON 含两个默认清单、记录、正确 schemaVersion/app。
- **SAF 恢复**：`pm clear` 清空 App 数据后启动（无样例记录），设置 → 从文件恢复 → 选择该 JSON；预览显示“2 个清单、3 条记录；新增 0 个清单、3 条记录，跳过 2 项已有数据”；确认合并后 3 条记录恢复。重复导入的 UI 流程未第二次完整执行；同 ID 幂等由 `RepositoryTest.backupExportAndImportIsIdempotentAndRenamesConflicts` 覆盖。
- **进程回收**：`am force-stop` 后冷启动，记录仍在，清单正常读取。
- **系统重启**：`adb reboot` 完成启动后打开 App，记录仍在；真实系统 Launcher 的小部件实例未验证（无系统 Launcher 绑定）。
- **深/浅色**：系统 night no/yes 下分别截图，App 与 Widget 文本/背景可读；App 显式主题切换与偏好持久化由 `settingsThemeAndLiteralSearch` 覆盖。
- **字体缩放**：`font_scale 2.0` 下 App 编辑页保存按钮可见、可点击；Widget 至少保留标题、1 行记录、“＋记一条”和页脚入口；验证后恢复 1.0。
- **输入法**：编辑器自动聚焦并显示键盘，保存按钮在键盘上方；UI 测试在键盘存在时点击保存成功；Back 第一次收键盘、第二次保留草稿退出。
- **APK 原位升级**：本次开发中多次 `install -r` 覆盖安装，Room 数据与偏好保留；不是正式版本间升级矩阵测试。
- **检查更新**：Release 创建前手动验证 404 文案“仓库还没有发布版本”；发布 `v0.2.0` 后安装 Release APK，设置 → 检查更新返回“已是最新版本：v0.2.0”，点“打开发布页”成功进入 Chrome。
- **多实例/双尺寸**：真实系统 `AppWidgetHost` 中两个标准实例同数据刷新；标准+大号实例可绑定不同清单/主题/密度，一个实例完成只在该实例出现撤销；大号显示行数多于标准；配置页保存立即刷新，取消不产生 config。

截图：

- [浅色主界面](android-main-light.png)
- [深色主界面](android-main-dark.png)
- [浅色 Widget](android-widget-light.png)
- [深色 Widget（200% 字体）](android-widget-dark-200pct.png)
- [检查更新结果](android-update-check.png)

### 未执行 / 未通过

- 小米/HyperOS 3 真机普通桌面与负一屏；不将本机 `AppWidgetHost` 结果视为小米桌面通过。
- API26、API31、API36 运行；当前仅 API34。
- TalkBack 完整人工验收、键盘导航全流程、所有页面 200% 字体逐项人工走查。
- 性能 P95（Widget 点击到可输入、保存到桌面可见）、10,000 条记录真机流畅度。
- 数据库存储写满/IO 故障注入、真实断电草稿边界。
- 真实系统 Launcher 的小部件在重启/APK 升级/进程回收后的实例恢复。
- 重复 SAF 导入的手机端第二次完整点击流程（逻辑与 Room 测试已覆盖）。

### GitHub 发布

- 源码：<https://github.com/wacilimonster-source/wanglemai>
- Release：<https://github.com/wacilimonster-source/wanglemai/releases/tag/v0.2.0>
- APK 资产：`wanglemai-0.2.0-debug.apk`，Release 上下载哈希与本地一致：`e10616e56212ae80b356936f3881f2bd487ab20362218b0b51055e596527db90`。
- 检查更新读取 `api.github.com/repos/wacilimonster-source/wanglemai/releases/latest`，无 token，不自动安装。

### 已知限制与下一步

1. 获得明确小米设备后，先验证普通桌面标准 Widget，再单独验证负一屏入口；记录完整系统/桌面/智能助理版本。
2. 补 API26/API31/API36 矩阵、TalkBack/大字体人工验收、性能 P50/P95。
3. 在真实 Launcher 上验证 Widget 重启恢复、升级、进程回收与宿主主题缓存。
4. 如需小米小部件中心分发或负一屏专属适配，另立变更并按官方审核流程，不擅自 Root/改系统/上架。

---

## 历史：0.1.0-framework 基线（2026-11-06）

已从仅有产品原型的项目搭建原生工程并构建 debug APK；当时仅覆盖基础闭环。

- 实际环境：Windows、Temurin JDK17、Android SDK36、`emulator-5558`（API34）。
- JVM 6/6、设备 10/10（Room 8 + 基础 UI 1 + 系统 AppWidgetHost 1）通过。
- 证据：`docs/evidence/android-build.txt`、`android-instrumentation.txt`、`android-test-summary.json`。
- 当时边界：无自定义清单/迁移、批量、长期草稿、SAF 备份、实例主题密度；快速编辑为全页；主页空态未区分加载。
- 未通过项与小部件/负一屏前置验证同当前限制；0.1.0 的 `releases/wanglemai-0.1.0-debug.apk` 保留供对照，不应作为完整 MVP 使用。

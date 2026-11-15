# 忘了没 · Android MVP 交付 v0.4.2

日期：2026-11-15。状态：**v0.4.2 自适应图标与桌面触点完善已发布，MuMu（Android 15 / API 35）全量自动化验证通过**；v0.4.0 的逐屏视觉比对与模拟器 Launcher 小部件恢复验证仍然有效。澎湃 OS 3 真机普通桌面、API26/31、TalkBack 人工验收和性能目标仍未验证。已确认系统负一屏只接受小米小部件；因本项目不上架应用商店，小米小部件准入不成立，**负一屏取消开发**。

## 当前交付

- GitHub 仓库：<https://github.com/wacilimonster-source/wanglemai>
- GitHub Releases（检查更新使用）：<https://github.com/wacilimonster-source/wanglemai/releases>
- 原生工程：`app/`，Kotlin + Jetpack Compose/Material3 + Room + Coroutines/StateFlow。
- Debug APK：[wanglemai-0.4.2-debug.apk](https://github.com/wacilimonster-source/wanglemai/releases/download/v0.4.2/wanglemai-0.4.2-debug.apk)（versionCode 13，applicationId `com.wanglemai.app`，minSdk26 / compile+targetSdk36）。
- 校验和：[SHA256SUMS.txt](releases/SHA256SUMS.txt)；v0.4.2 APK SHA256 见 SHA256SUMS。
- 实测记录：[开发记录](docs/development-log.md)、[验收记录](docs/03-acceptance.md)、[框架状态](docs/07-framework-status.md)。
- 自动化证据：[构建与 JVM](docs/evidence/android-build-0.3.5.txt)、[设备测试](docs/evidence/android-instrumentation-0.3.5.txt)、[摘要 JSON](docs/evidence/android-test-summary-0.3.5.json)（v0.3.5 基准记录；v0.4.0 实测见 [开发记录](docs/development-log.md)）。

## 本版改进（v0.4.2）

- **自适应启动图标**：`ic_launcher` / `ic_launcher_round` 三层自适应图标（深烘炭墨底 + 斜置叠层便签 + 朱砂索引耳），Android 13+ 支持单色主题图标（Material You 着色）。
- **小部件空白区点按新增**：点按小部件空白区域（触点之外的间隙、行下方留白与边缘）等同"＋记一条"，直接打开快速录入；既有触点自身消费点击、不冒泡。
- **小部件编辑器关闭回桌面**：从桌面小部件进入编辑器，保存、删除或收起后回到桌面，不再把后台 App 主界面带到前台；从 App 内进入的行为不变。
- **EditorialMetrics 尺寸令牌统一**：全 App 最小触控目标 48dp、按钮改为 `heightIn(min=…)` 自适应大字体；设置/清单管理/小部件配置页按钮收敛为共享组件，交互尺寸与圆角/字号全部走共享令牌。

## v0.4.1 改动

v0.4.0 全量代码评审修复 `CR-01`~`CR-16`：备份导出防残留损坏、BOM 兼容、备份合并与跨清单排序追加、幂等命令表清理、协程异常与取消传播兜底、更新模块并发与完整性加固、Widget 恢复/配置解耦、清单状态与旋转保活等，详见 [Release 说明](docs/release-notes-0.4.1.md) 与 [评审记录](docs/code-review-2026-11-15.md)。

## v0.4.0 改动

- **日式活页手账视觉焕新（Editorial Edition）**：参照 [prototype/warm/index.html](prototype/warm/index.html) 全面重构五屏 Compose UI 与 Widget 细节——道林暖白 / 深烘炭墨双色板、三层叠页卡片、朱砂双线日历印章、无缝衔接的阶梯活页索引耳、44dp 贯穿朱砂边线 + 45° 菱形复选框、点阵撕页快记、书签分册目录与 `EX LIBRIS` 藏书印记、桌面壁纸仿真小组件配置预览及罗马数字设置页。
- 移除「待买」类型，只保留待办：底部导航、编辑器数量/单位、小部件数量列与相关文案全部删除。
- 数据库 v1→v2 迁移：待买清单（含默认「日常采购」）删除，其中记录追加到「随手待办」末尾并清除数量/单位；绑定待买清单的小部件自动改绑「随手待办」；旧类型偏好键清理。
- 备份兼容：仍可导入 v0.3.x 备份（旧待买清单按同一策略并入随手待办，数量/单位丢弃）；新导出的备份保留 `kind:"TODO"` 字段，旧版本 App 也能导入。
- 清单重名检查从“同类型内”改为全局唯一（单一类型下行为一致）。

## v0.3.5 改动

- 编辑已有记录不再自动弹出键盘；新增记录和“保存并继续”保持自动聚焦。
- 开机、覆盖安装、回到 App 时刷新小部件；选择器改用带示例记录的静态预览。

## 已实现的产品能力

v0.3.2小部件精修与验证见 [精修规格](docs/widget-refinement/design-spec.md#10-原生实现与验证--2026-11-07) 和 [原生验证记录](docs/widget-refinement/verification-0.3.2.json)，安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。

- 单一待办类型、一个不可删除的默认清单「随手待办」；自定义清单新建/重命名/删除，删除时把记录与草稿迁移到默认清单并重绑小部件。
- 记录新增/编辑/软删除、置顶、固定排序、完成/恢复、5 秒进程内撤销、版本条件写入。
- 最多 50 条原子批量录入，按 CRLF/LF 拆分、忽略空行、错误指出原始行号、同 commandId 重试不重复写入。
- 字面搜索（忽略英文大小写，`%`/`_` 不作通配符）、按清单/状态过滤、清单切换记忆。
- Room 长期草稿：300ms debounce、onStop 补写、草稿恢复/丢弃、编辑冲突不静默覆盖、保存成功才清除。
- 标准/大号两种 AppWidget，各自可绑定任意清单并独立设置主题（跟随系统/浅色/深色）与舒适、紧凑、超紧凑三档密度。支持两排桌面高度，舒适按尺寸保留底栏，紧凑把操作收进标题栏，舒适/紧凑48dp、超紧凑36dp完成触区，按空间显示最多 12 条。暖调半透明玻璃底色采用浅色奶油 92% / 深色暖棕 90%、局部顶部高光和细描边，圆角跟随系统。配置预览复用真实 RemoteViews，保留多实例独立、刷新、完成撤销和错误反馈；空态区分“全部完成”与“空清单”，选择器使用带示例记录的静态预览，开机/覆盖安装/回到 App 时主动刷新。详见 [小部件精修规格](docs/widget-refinement/design-spec.md)。
- 暖米色视觉体系（2026-11 提案 v1，见 docs/visual-refresh/）：奶油底 + 暖纸白内容面 + 焦糖棕主色，圆形完成控件、分段筛选、整宽新增入口、24dp 线性矢量图标；浅/深两套显式 token。
- Widget 的新增/正文/标题/空白区点击直接通过 `PendingIntent.getActivity` 进入 `QuickEntryActivity`，没有 Receiver/Service 间接启动编辑页；从小部件进入的编辑器运行在独立 on-home 任务，关闭后回桌面。
- SAF JSON 导出与导入：5MB 上限、严格 schema 校验、预览数量、事务合并、同 ID 保留本机、名称冲突加“（导入N）”、失败不部分写库。
- 浅/深色主题；应用主题可跟随系统或显式切换。
- 设置页“检查更新”：GitHub Releases API 优先，失败时回退到 jsDelivr 静态清单；发现新版本后在客户端内直接下载安装包（国内加速优先，失败自动改用 GitHub），下载时流式校验 SHA256，完成后由用户确认调起系统安装器。仅手动触发，无后台轮询。

## 快速构建与测试

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest

# 本机已明确选定的模拟器；不要向所有已连接设备批量安装
adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r com.wanglemai.app.test/androidx.test.runner.AndroidJUnitRunner
```

实际结果（v0.4.2）：JVM 72/72 通过；设备 53/53 完整重跑通过（MuMu `LGE-AN10`，Android 15 / API 35）。历史上首轮仪器测试偶发过一次 10 秒内未定位新记录的失败，单例重跑通过；不以单例结果冒充全绿。Gradle 的 `connectedDebugAndroidTest` 在当前环境可能回传 “Failed to receive the UTP test results”，但测试 XML 为 0 失败；以 `am instrument` 输出为准。

## 发布新版本（GitHub Actions）

发版由 `.github/workflows/release.yml` 完成，不再在本机构建上传。一次性配置与排障见 [CI 发版说明](docs/ci-release.md)。

1. （可选）写 `docs/release-notes-X.Y.Z.md`，Release 正文会优先采用；没有则使用 GitHub 自动生成的 notes。
2. 打开 Actions → Release → Run workflow，填入 `version`（如 `0.4.0`）和可选 `release_name`；首次发布建议先勾选 `dry_run`，验证构建、测试和签名。
3. 正式运行会自动：递增 versionCode、构建并跑 JVM 单测、校验签名证书、写入 APK 与 `releases/SHA256SUMS.txt`、生成 `update/latest.json`、提交 main、打 tag、创建 GitHub Release、清理 jsDelivr 清单缓存。
4. 验证 Release 资产、`update/latest.json` 的 SHA256 与实际 APK 一致，`https://cdn.jsdelivr.net/gh/wacilimonster-source/wanglemai@vX.Y.Z/releases/wanglemai-X.Y.Z-debug.apk` 可下载，并用新 APK 覆盖安装旧版确认签名兼容。

本地手工回退（Actions 不可用时）：改版本号 → `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest` → 复制 APK 到 `releases/` → `python scripts/update-manifest.py ...` → 提交、打 tag → 手动创建 Release 并上传 APK → `bash scripts/purge-jsdelivr-manifest.sh`。jsDelivr 静态清单使用 `@main`，APK 加速链接使用 tag，避免内容漂移。

## 安装与添加小部件

1. 安装 APK，启动“忘了没”，新增一条记录。
2. 在系统桌面长按 → 小部件，选择“忘了没·标准清单”或“忘了没·大清单”。
3. 在配置页选择清单、主题和密度，保存后立即生成首帧；取消不会产生可用实例。
4. 点小部件标题/“查看全部”打开 App，点“＋记一条”或记录正文直接打开编辑页，点圆圈在桌面完成；完成后页脚可撤销。
5. App 设置页也提供两个标准小部件的 pin 请求按钮，系统不支持时从桌面小部件入口添加。

## 澎湃 OS 3 与负一屏

本版本只使用标准 Android Widget，Provider 与 Activity 在应用默认进程，不依赖小米小部件中心审核、专有独立进程或系统修改。

**负一屏实测反馈（用户提供，机型/完整系统版本待补）：** 澎湃 OS 3 负一屏只支持小米小部件，标准 Android 原生小部件不在可添加列表；“忘了没”的原生 Widget 只能添加到普通桌面。因此当前版本**不支持负一屏**，不能把系统 `AppWidgetHost` 测试当成负一屏通过。

**负一屏可行性结论（2026-11-06）：不开发。** 小米官方小部件接入要求应用先在小米应用商店上传/更新并通过应用商店审核，使用企业开发者账号，再在小部件开放平台提交小部件审核；APP 被下架时小部件也会下架。官方文档未提供侧载 APK、本地调试或白名单进入负一屏的路径。本项目明确“不上架应用商店、只自有 APK 下发”，因此不满足小米小部件的准入前提，继续开发 `miuiWidget`/独立进程也无法在目标负一屏验证或上线。

## 文件导航

| 文件 | 用途 |
|---|---|
| docs/01-product.md | 产品范围、页面、字段与交互规则 |
| docs/02-architecture.md | 数据模型、接口、Widget 生命周期、备份协议 |
| docs/03-acceptance.md | 验收用例、实测结果、设备矩阵、发布门槛 |
| docs/04-agent-tasks.md | 依赖顺序与交接边界 |
| docs/05-dev-prompt.md | 开发启动任务 |
| docs/07-framework-status.md | 当前实现边界、工具链、安装与负一屏准入结论 |
| docs/development-log.md | 实际构建/测试/人工验证结果与限制 |
| prototype/ | 浏览器交互参考，不作为 Android 实现或验收证据 |
| app/、gradle/、gradlew* | 原生工程、Room schema、Gradle Wrapper |

## 已知限制

- 未在小米/HyperOS 3 真机验证普通桌面；系统桌面刷新、宿主缓存、省电策略结论待补。
- 负一屏只接受小米小部件，而小米小部件要求应用商店上架/审核；自有 APK 下发不满足，已取消负一屏开发。
- `INTERNET` 权限仅用于用户手动检查更新和下载安装包；`REQUEST_INSTALL_PACKAGES` 仅用于调起系统安装器（首次安装更新会引导开启“安装未知应用”）；没有账号、云同步或后台网络任务，安装始终由用户在系统弹窗确认。
- 只验证 Android 14/API34 模拟器；API26、API31、API36 运行未执行。
- TalkBack 只做了语义与触控目标实现检查，未做完整人工验收；性能 P95 未测量。
- 系统重启和覆盖安装后的小部件恢复已在 Android 14 模拟器（pika_test / AOSP Launcher）验证；HyperOS 3 真机 Launcher 仍未验证。
- 数据库写满/存储失败未做故障注入；输入保留与重试由实现保证但未专门测试。
- 草稿 onStop 写盘不能保证突然断电前最后 300ms 的输入。
- jsDelivr 国内 CDN 的可用性不保证；客户端内下载失败会自动改用 GitHub，公开分发的 debug 签名不应作为安全更新身份。
- RemoteViews 不支持实时背景模糊；小部件采用半透明色面 + 1dp 描边模拟玻璃效果。

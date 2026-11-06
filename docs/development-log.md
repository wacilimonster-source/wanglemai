# Android 开发与交接记录

## 当前交付：0.2.0-mvp / 2026-11-06

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
- **检查更新**：Release 创建前手动验证 404 文案“仓库还没有发布版本”；创建 GitHub Release 后再验证“已是最新版本”与下载/发布页。
- **多实例/双尺寸**：真实系统 `AppWidgetHost` 中两个标准实例同数据刷新；标准+大号实例可绑定不同清单/主题/密度，一个实例完成只在该实例出现撤销；大号显示行数多于标准；配置页保存立即刷新，取消不产生 config。

截图：

- [浅色主界面](android-main-light.png)
- [深色主界面](android-main-dark.png)
- [浅色 Widget](android-widget-light.png)
- [深色 Widget（200% 字体）](android-widget-dark-200pct.png)

### 未执行 / 未通过

- 小米/HyperOS 3 真机普通桌面与负一屏；不将本机 `AppWidgetHost` 结果视为小米桌面通过。
- API26、API31、API36 运行；当前仅 API34。
- TalkBack 完整人工验收、键盘导航全流程、所有页面 200% 字体逐项人工走查。
- 性能 P95（Widget 点击到可输入、保存到桌面可见）、10,000 条记录真机流畅度。
- 数据库存储写满/IO 故障注入、真实断电草稿边界。
- 真实系统 Launcher 的小部件在重启/APK 升级/进程回收后的实例恢复。
- 重复 SAF 导入的手机端第二次完整点击流程（逻辑与 Room 测试已覆盖）。

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

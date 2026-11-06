# Android MVP 实现状态 v0.2.1

更新日期：2026-11-06。此文件区分“已实现源码/模拟器实测”与“仍需真机验收”的范围。完整目标见产品规格与验收清单；实际命令与结果见 [开发记录](development-log.md)。

## 已实现的源码

- 单 app 模块，Kotlin/Compose Material3 + Room/KSP + Coroutines/StateFlow，手动 AppContainer；除用户手动触发 GitHub Release 检查外无网络层；无 Hilt、WorkManager 或服务端。
- Room v1：清单、记录、小部件配置、撤销、提交防重、草稿、偏好；无破坏性迁移。两个默认清单初始化幂等。
- 领域校验：标题/备注 Unicode 码点限制、数量 BigDecimal、清单名 1～20 码点且同类忽略大小写不重名、批量 50 条原始行号。
- 记录：新增/编辑/软删除、置顶、固定排序、跨清单移动（移到待办清除数量/单位并确认）、完成/恢复、版本条件更新、App 5 秒撤销、Widget 撤销。
- 自定义清单：新建、重命名、删除迁移；删除事务迁移全部记录（含软删除）、重绑小部件、清理撤销、迁移/保留草稿。
- 批量：CRLF/LF、trim、忽略空行、1～50 条、错误行号、整批事务、commandId 防重。
- 草稿：`create:{listId}` / `edit:{itemId}`，300ms debounce、onStop 补写、恢复/丢弃、版本冲突提示、保存成功清除；写盘带 epoch 防旧草稿覆盖。
- App：清单选择与类型记忆、搜索、未完成/已完成、完成恢复、管理清单、设置、主题、SAF 备份导出/导入预览与事务合并。
- 更新检查：设置页手动读取 GitHub Releases latest；失败时回退到 jsDelivr 静态清单；展示发布页、GitHub 下载、国内加速下载和 SHA256；不自动下载或安装，无后台轮询。
- 快速编辑：独立 `QuickEntryActivity`，底部面板样式，自动聚焦并请求 IME，返回优先收键盘，Back/取消保留草稿，键盘不遮挡保存。
- Widget：标准/大号两个 Provider，XML RemoteViews；每实例 listId/theme/density；多实例独立；尺寸/字体缩放计算行数；显式浅/深色资源；完成、撤销、冲突页脚、空态/错误态；写入后主动刷新。
- 交互链路：Widget 新增/正文/标题直接 `PendingIntent.getActivity`；完成通过内部 Receiver（`goAsync`）执行，不使用 Activity trampoline、Service 或常驻后台服务。
- SAF：5MB 流式上限、严格 schemaVersion/app/字段/引用/数量校验、预览不写库、确认时事务内重新校验、同 ID 保留、名称冲突加后缀、失败整体回滚；导出不含草稿/Widget ID/设备设置。
- 主题：应用主题跟随系统或显式浅/深；Widget 每实例 SYSTEM/LIGHT/DARK。SYSTEM 在进程存活时响应配置变化重绘，宿主缓存仍需真机复核。

## 工具链（固定版本）

Gradle 8.14.3、AGP 8.13.1、Kotlin 1.9.24、KSP 1.9.24-1.0.20、Compose Compiler 1.5.14、Compose BOM 2024.06.00、Room 2.6.1、Coroutines 1.8.1、Activity 1.9.3、Lifecycle 2.8.3、androidx.core 1.13.1。版本定义见 `gradle/libs.versions.toml`。

minSdk26 / compileSdk36 / targetSdk36；JDK17。`local.properties` 是本机 SDK 路径，已忽略。工程仅申请 INTERNET 用于手动检查 GitHub Releases；无麦克风、通知、精确闹钟、悬浮窗或广泛存储权限；另有 AndroidX 合入包内 signature 动态 Receiver 权限。

## 构建、测试与安装

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest
adb -s <明确选定的设备> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <明确选定的设备> install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s <明确选定的设备> shell am instrument -w -r com.wanglemai.app.test/androidx.test.runner.AndroidJUnitRunner
```

在约定环境（`emulator-5558`，Android14/API34，sdk_gphone64_x86_64）实际结果：JVM 38/38、设备 29/29 通过。不要向所有已连接设备批量安装；其他连接设备未操作。`connectedDebugAndroidTest` 的 UTP 结果回传在该环境有已知告警，以 `am instrument` 的 `OK (N tests)` 和测试 XML 为准。

GitHub 仓库：<https://github.com/wacilimonster-source/wanglemai>。现成 APK：[wanglemai-0.2.1-debug.apk](https://github.com/wacilimonster-source/wanglemai/releases/download/v0.2.1/wanglemai-0.2.1-debug.apk)。设置页的“检查更新”优先读取 GitHub Releases latest，失败时回退到 jsDelivr 静态清单；国内加速下载仍只打开浏览器并显示 SHA256。

## 未完成/未验收

1. 小米/HyperOS 3 真机：普通桌面、负一屏、系统宿主缓存、省电策略、输入法、字体/网格、重启与 APK 原位升级。
2. 真实系统 Launcher 的小部件多实例生命周期（重启、恢复 ID、升级）；目前用 debug 专用 `AppWidgetHost` 验证协议与渲染，不等同小米桌面。
3. API26、API31、API36 真机/模拟器运行；当前只有 API34。
4. TalkBack 完整人工验收、键盘导航、200% 字体下的全部页面逐项人工确认（已做 200% 截图与核心控件存在性测试）。
5. 性能 P95（Widget 点击到可输入、保存到桌面可见）未测量；10,000 条记录下真机流畅度未测。
6. 数据库写满/存储故障注入、真实断电草稿边界。
7. 负一屏接纳标准 Widget 的结论；未提供机型/完整系统版本，不得声称适配完成。

## 澎湃 OS 3 负一屏前置验证

确认范围：用户只提供 HyperOS 3，尚未提供手机型号、完整系统构建号、Android 版本和智能助理版本。

1. 安装 debug APK，先在普通桌面添加“标准清单”和“大清单”，验证显示、正文/新增跳转、完成撤销、多实例刷新、重启恢复。
2. 在原生负一屏添加入口检查本地“忘了没”是否可选择；或检查系统是否允许把标准小部件移入负一屏。
3. 如能添加，分别验证显示、编辑返回、完成撤销、多实例刷新、重启恢复，记录具体系统/桌面/智能助理版本。
4. 如只能在普通桌面添加，记录为“标准 Widget 无法通过当前原生入口添加至负一屏”，不得据此声称完成负一屏适配。
5. 若负一屏是硬性目标，再单独评估小米专属适配与官方审核/开发测试环境。不得自动改系统、Root、安装修改版智能助理或擅自上架。

自行安装使用标准桌面 Widget 不需小米审核；小米小部件中心正式分发须按官方流程审核。仅凭 HyperOS 3 版本号不能保证本地标准 Widget 可放入负一屏。

## 测试记录

本次构建、29 项设备测试、SAF/重启/主题/字体人工验证和未执行项均在 [开发记录](development-log.md) 与 [验收清单](03-acceptance.md) 如实记录。“已实现源码”不等于全部真机验收通过。

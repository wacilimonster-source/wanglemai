# 忘了没 · Android MVP 交付 v0.2.0

日期：2026-11-06。状态：**原生 MVP 功能已实现并在 Android 14/API34 模拟器完成自动化验证**；澎湃 OS 3 真机、负一屏、API26/31/36、TalkBack 人工验收和性能目标仍未验证。目标系统已确认澎湃 OS 3，手机型号与负一屏支持情况待补。

## 当前交付

- GitHub 仓库：<https://github.com/wacilimonster-source/wanglemai>
- GitHub Releases（检查更新使用）：<https://github.com/wacilimonster-source/wanglemai/releases>
- 原生工程：`app/`，Kotlin + Jetpack Compose/Material3 + Room + Coroutines/StateFlow。
- Debug APK：[wanglemai-0.2.0-debug.apk](https://github.com/wacilimonster-source/wanglemai/releases/download/v0.2.0/wanglemai-0.2.0-debug.apk)（versionCode 2，applicationId `com.wanglemai.app`，minSdk26 / compile+targetSdk36）。
- 校验和：[SHA256SUMS.txt](releases/SHA256SUMS.txt)。
- 实测记录：[开发记录](docs/development-log.md)、[验收记录](docs/03-acceptance.md)、[框架状态](docs/07-framework-status.md)。
- 自动化证据：[构建与 JVM](docs/evidence/android-build-0.2.0.txt)、[设备测试](docs/evidence/android-instrumentation-0.2.0.txt)、[摘要 JSON](docs/evidence/android-test-summary-0.2.0.json)。

## 已实现的产品能力

- 两种类型清单、两个不可删除的默认清单；自定义清单新建/重命名/删除，删除时把记录与草稿迁移到同类默认清单并重绑小部件。
- 记录新增/编辑/软删除、置顶、固定排序、待买数量与单位、完成/恢复、5 秒进程内撤销、版本条件写入。
- 最多 50 条原子批量录入，按 CRLF/LF 拆分、忽略空行、错误指出原始行号、同 commandId 重试不重复写入。
- 字面搜索（忽略英文大小写，`%`/`_` 不作通配符）、按清单/状态过滤、清单与类型切换记忆。
- Room 长期草稿：300ms debounce、onStop 补写、草稿恢复/丢弃、编辑冲突不静默覆盖、保存成功才清除。
- 标准/大号两种 AppWidget，各自可绑定任意清单并独立设置主题（跟随系统/浅色/深色）与密度；多实例独立、主动刷新、小部件内完成与撤销、冲突页脚、空态/错误态。
- Widget 的新增/正文/标题点击直接通过 `PendingIntent.getActivity` 进入 `QuickEntryActivity`，没有 Receiver/Service 间接启动编辑页。
- SAF JSON 导出与导入：5MB 上限、严格 schema 校验、预览数量、事务合并、同 ID 保留本机、名称冲突加“（导入N）”、失败不部分写库。
- 浅/深色主题；应用主题可跟随系统或显式切换。
- 设置页“检查更新”：按 GitHub Releases 查找新版本，可打开发布页或下载 APK；仅手动触发，不自动下载/安装。

## 快速构建与测试

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest

# 本机已明确选定的模拟器；不要向所有已连接设备批量安装
adb -s emulator-5558 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5558 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5558 shell am instrument -w -r com.wanglemai.app.test/androidx.test.runner.AndroidJUnitRunner
```

实际结果：JVM 33/33 通过；设备 29/29 通过（`OK (29 tests)`）。Gradle 的 `connectedDebugAndroidTest` 在当前环境可能回传 “Failed to receive the UTP test results”，但测试 XML 为 0 失败；以 `am instrument` 输出为准。

## 安装与添加小部件

1. 安装 APK，启动“忘了没”，新增一条记录。
2. 在系统桌面长按 → 小部件，选择“忘了没·标准清单”或“忘了没·大清单”。
3. 在配置页选择清单、主题和密度，保存后立即生成首帧；取消不会产生可用实例。
4. 点小部件标题/“查看全部”打开 App，点“＋记一条”或记录正文直接打开编辑页，点圆圈在桌面完成；完成后页脚可撤销。
5. App 设置页也提供两个标准小部件的 pin 请求按钮，系统不支持时从桌面小部件入口添加。

## 澎湃 OS 3 与负一屏

本版本只使用标准 Android Widget，Provider 与 Activity 在应用默认进程，不依赖小米小部件中心审核、专有独立进程或系统修改。普通桌面标准小部件已通过系统 `AppWidgetHost` 端到端测试，但这**不是**小米桌面或负一屏实测。

负一屏是否接纳本地标准 Widget 必须单独在真机验证：先确认普通桌面可用，再从系统负一屏添加入口检查；若只能放在普通桌面，应记录为“标准 Widget 无法通过当前原生入口添加至负一屏”，不得声称已完成负一屏适配。需要小米专属适配时另立变更并遵守官方审核流程，不擅自 Root、改系统或上架。

## 文件导航

| 文件 | 用途 |
|---|---|
| docs/01-product.md | 产品范围、页面、字段与交互规则 |
| docs/02-architecture.md | 数据模型、接口、Widget 生命周期、备份协议 |
| docs/03-acceptance.md | 验收用例、实测结果、设备矩阵、发布门槛 |
| docs/04-agent-tasks.md | 依赖顺序与交接边界 |
| docs/05-dev-prompt.md | 开发启动任务 |
| docs/07-framework-status.md | 当前实现边界、工具链、安装与负一屏验证步骤 |
| docs/development-log.md | 实际构建/测试/人工验证结果与限制 |
| prototype/ | 浏览器交互参考，不作为 Android 实现或验收证据 |
| app/、gradle/、gradlew* | 原生工程、Room schema、Gradle Wrapper |

## 已知限制

- 未在小米/HyperOS 3 真机验证；负一屏、系统桌面刷新、宿主缓存、省电策略结论待补。
- `INTERNET` 权限仅用于用户手动检查 GitHub Releases；没有账号、云同步或后台网络任务。
- 只验证 Android 14/API34 模拟器；API26、API31、API36 运行未执行。
- TalkBack 只做了语义与触控目标实现检查，未做完整人工验收；性能 P95 未测量。
- 系统重启只验证了 App 数据恢复；真实桌面小部件实例在重启/APK 升级后的恢复未在系统 Launcher 上验证。
- 数据库写满/存储失败未做故障注入；输入保留与重试由实现保证但未专门测试。
- 草稿 onStop 写盘不能保证突然断电前最后 300ms 的输入。

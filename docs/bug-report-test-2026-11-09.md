# 忘了没 · 全量测试与 Bug 清单

日期：2026-11-09
基线：`3217b65`（v0.3.3-mvp，versionCode 8）
测试范围：JVM 单元测试、Android Lint、Android 仪器测试、模拟器手工场景验证
结论：**未发现正常主流程下的数据丢失或重复写入**；确认 7 个 Bug，其中 1 个可稳定触发崩溃、3 个影响日常体验/可访问性、3 个为边缘场景缺陷。**7 个问题已全部修复并复测通过，随 v0.3.4 发布。**

### 修复与复测结果（2026-11-09）

| 项目 | 修复后结果 |
|---|---|
| JVM 单元测试 | **68/68 通过**（新增 10 项回归：深层 JSON 拒绝、清单名换行拒绝、下载版本绑定、失败/取消保留旧包等） |
| Android 仪器测试 | **43/43 通过**（API 34；首轮受设备上残留 Launcher“添加到桌面”弹窗影响出现 4 个环境性失败，清理 Launcher 状态后完整重跑全绿） |
| Android Lint | **0 error，61 warning** |
| 手工复测 | 深层 JSON 安全拒绝且进程存活；换行清单名返回明确错误；浅色编辑页系统栏图标可读；200% 字号三项主题完整可选；连续完成时最新撤销条完整保留约 5 秒；导出→导入回环正常 |

修复要点：

- BUG-01：撤销条改用带身份的自定义 `SnackbarVisuals`，5 秒计时从该条**真正显示**时开始；新完成替换旧撤销条，其他 Snackbar 不受影响。
- BUG-02：移除编辑页无条件白色系统栏设置，改由 `WanglemaiTheme` 按实际主题同步图标明暗。
- BUG-03：设置页主题选项改为 `FlowRow`，200% 字号自动换行。
- BUG-04：`BackupCodec` 解析前做线性嵌套深度检查（上限 64），并兜底捕获 `StackOverflowError` 转为 `BackupException`。
- BUG-05/06：`Ready` 状态记录版本，检查更新时丢弃过期包；下载写临时文件、校验通过后原子替换，失败/取消保留原安装包并在 UI 提供“安装已下载版本”。
- BUG-07：`normalizeListName` 拒绝换行、行分隔符和控制字符，导入时返回具体原因。


---

## 1. 测试结果摘要

| 项目 | 结果 | 说明 |
|---|---|---|
| JVM 单元测试 | **58/58 通过** | ApkDownloader 14、UpdateCenter 6、UpdateChecker 13、Validation 13、BackupCodec 12 |
| 设备仪器测试 | **43/43 通过** | `emulator-5554`，Android 14 / API 34，1080×1920，420dpi（AVD `pika_test`） |
| Android Lint | **0 error，61 warning** | 警告均为依赖版本、`targetCell*` 低版本兼容提示、Widget 预览布局硬编码文案等非阻断项 |
| 手工验证 | 通过 | 导出→导入回环、在线/离线检查更新、主题切换、200% 字号、三键导航、真实桌面小部件完成/撤销 |
| 崩溃复现 | 已复现 | 导入深层嵌套 JSON 导致 `StackOverflowError`，进程退出（见 BUG-04） |

测试命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r -t app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r com.wanglemai.app.test/androidx.test.runner.AndroidJUnitRunner
```

---

## 2. Bug 清单总表

| ID | 优先级 | 模块 | 现象摘要 | 状态 |
|---|---|---|---|---|
| BUG-01 | P2 | 主清单 / 撤销 | 连续完成两条记录时，第二条撤销条几乎立即消失，5 秒撤销窗口名存实亡；特定时序下还会提前关掉无关提示或让撤销条无限停留 | 已修复，手工复测通过 |
| BUG-02 | P2 | 编辑页 / 系统栏 | 浅色主题下编辑页状态栏图标为白色，压在浅色遮罩上几乎不可见；三键导航栏图标同样不匹配 | 已修复，截图复测通过 |
| BUG-03 | P2 | 设置页 / 可访问性 | 系统字号 200% 时，“应用主题”一行的“深色”选项被挤出屏幕，完全无法选择 | 已修复，200% 字号复测通过 |
| BUG-04 | P1 | 备份导入 | 选择深层嵌套的 JSON 备份会触发 `StackOverflowError`，App 直接崩溃，未按预期提示“格式不支持” | 已修复，崩溃不再复现 |
| BUG-05 | P3 | 设置 / 更新 | 下载好的安装包与“检查更新”结果没有版本绑定：检查到新版本后仍可能安装旧包；检查失败时已下载的安装入口直接消失 | 已修复，单测覆盖，发版后端到端复测 |
| BUG-06 | P3 | 设置 / 更新 | 在“已下载”状态点“重新下载”后取消或断网，会把原先已校验的安装包删除，安装入口一并丢失 | 已修复，单测覆盖，发版后端到端复测 |
| BUG-07 | P3 | 清单 / 备份导入 | 备份中的清单名可包含换行符，导入后管理页/下拉菜单出现多行清单名；正常 UI 输入路径无法产生 | 已修复，导入复测通过 |

> 优先级定义：P1 崩溃/数据安全；P2 明显影响日常使用或可访问性；P3 边缘场景或低概率体验问题。

---

## 3. 详细说明

### BUG-01 · 连续完成时撤销条计时错位（P2）

**位置**

- `app/src/main/java/com/wanglemai/app/ui/main/MainActivity.kt:190-204`（`report` 调用撤销条）
- `app/src/main/java/com/wanglemai/app/ui/main/MainActivity.kt:470-480`（`showUndo`）

**现象**

在“未完成”列表里 1 秒内连续完成两条记录：

- 第一条撤销条正常显示；
- 第二条撤销条要排队等第一条消失后才出现，但**出现约 0.7 秒就被关闭**，而不是 5 秒；
- 如果此时另有较长时长的 Snackbar（例如更新下载完成提示）正在显示，计时器会把它提前关掉，随后撤销条因为没有后续计时器而**无限停留**，并阻塞后续所有 Snackbar。

**复现步骤**

1. 待办清单中至少保留两条记录。
2. 先点第一条的完成圆圈，约 0.7 秒后再点第二条的完成圆圈。
3. 观察底部撤销条：第二条撤销条的可见时间远小于 5 秒（实测约 0.7 秒）。

**根因**

`showUndo` 在调用 `showSnackbar` 之前就启动了 5 秒计时器。`SnackbarHostState.showSnackbar` 在已有 Snackbar 时会挂起排队，因此计时器在“排队期间”就开始倒计时；计时器到期时执行的是 `currentSnackbarData?.dismiss()`，关闭的是**当时正在显示的任意 Snackbar**，不一定是本次请求的那一条。

**正确修复后的效果**

- 每次完成/恢动作的撤销条从**真正显示**开始完整保留 5 秒；
- 第二条撤销条不会在 0.7 秒后消失，用户有完整撤销机会；
- 不会误关下载完成、错误提示等无关 Snackbar；
- 不会出现无限停留、阻塞后续提示的 Snackbar。

建议实现方向：让计时器绑定该次 Snackbar 实例（例如观察 `currentSnackbarData` 的身份后再启动超时），或在新的完成动作到来时主动替换上一条撤销条并重新计时。

**证据**

- `test-results/manual-undo-t2.png`（第二条完成约 2.9 秒时仍在显示）
- `test-results/manual-undo-t5.png`、`test-results/manual-undo-t7.png`（约 6 秒后已全部消失）
- 连续两次完成期间活跃数从 28 降到 26，确认两条都提交成功。

---

### BUG-02 · 浅色编辑页系统栏图标不可读（P2）

**位置**

- `app/src/main/java/com/wanglemai/app/ui/editor/QuickEntryActivity.kt:89-92`
- `app/src/main/java/com/wanglemai/app/ui/editor/QuickEntryActivity.kt:99`（`syncSystemBars = false`）
- `app/src/main/res/values/styles.xml` 的 `Theme.Wanglemai.Sheet`（`android:windowLightStatusBar=false`）

**现象**

- 系统或 App 处于浅色主题时，打开“记一条”编辑页，状态栏时间、信号、电量等图标仍是白色，压在浅色暖棕遮罩上，对比度极低（截图里几乎看不清时间）。
- 三键导航模式下，导航栏被系统加上灰色底、图标仍为白色，与浅色页面不协调。

**复现步骤**

1. 系统浅色或 App 主题切到“浅色”。
2. 主页面点“＋ 记一条”。
3. 观察顶部状态栏图标：白色图标叠在浅色背景上。

**根因**

`QuickEntryActivity` 在 `onCreate` 里无条件写入 `isAppearanceLightStatusBars = false` 和 `isAppearanceLightNavigationBars = false`，而 Compose 主题又通过 `syncSystemBars = false` 关闭了自动同步，因此浅色/深色主题共用“白色图标”的固定设置。

**正确修复后的效果**

- 编辑页跟随自身主题：浅色主题下状态栏/导航栏使用深色图标，深色主题下使用白色图标；
- 浅色模式下时间、电量、返回手势清晰可读；
- App 浅色 + 系统深色、App 深色 + 系统浅色两种组合都正确。

建议实现方向：根据 `state.theme` 解析出的实际亮暗（或直接开启 `WanglemaiTheme` 的 `syncSystemBars`）动态设置两个 `isAppearanceLight*` 标志；注意主题在 `load()` 完成后才可用，首帧可先按系统值设置再校正。

**证据**

- `test-results/manual-editor-light.png`（系统浅色）
- `test-results/manual-editor-light-app-dark-system.png`（App 浅色 + 系统深色）
- `test-results/manual-editor-3button-nokb.png`（三键导航）

---

### BUG-03 · 200% 字号下“深色”主题选项被挤出屏幕（P2）

**位置**

- `app/src/main/java/com/wanglemai/app/ui/settings/SettingsScreen.kt:118-136`

**现象**

系统字号调到 200% 后，设置页“外观与桌面”里的“应用主题”一行只能看到“跟随系统”和挤压成两行的“浅色”，**“深色”完全不在屏幕上，也不在无障碍树中**，用户无法切换到深色主题。字号 100% 时三个选项正常。

**复现步骤**

1. 系统设置 → 显示 → 字体大小调到最大（200%）。
2. 打开 App → 设置 → 外观与桌面。
3. 观察“应用主题”一行。

`uiautomator dump` 在 200% 下只包含：

```
'跟随系统' [455,666][727,765]
'浅色'     [832,652][922,849]
```

没有“深色”节点；100% 时三个选项都存在。

**根因**

`Row` 内先放“应用主题”标签和固定 16dp 间距，再放一个横向 `Row` 装三个 `FilterChip`；`Row` 不会自动换行，子项宽度超出约束后后面的 Chip 被压成 0 宽/移出可视区域。

**正确修复后的效果**

- 100%、130%、200% 字号下三个主题选项都完整可见、可点击、可被 TalkBack 读出；
- “浅色”不再被压成竖排；
- 不改变设置页其他分组布局。

建议实现方向：主题选项单独换行，使用 `FlowRow` 或垂直排列；与“小部件配置页”已经采用 `FlowRow` 的密度选项保持一致。

**证据**

- `test-results/manual-settings-font2.png`（只有两个选项可见）
- `test-results/ui-settings-font2.xml`（无障碍树缺少“深色”节点）
- `test-results/manual-settings-top-font1.png`（100% 时三项正常）

---

### BUG-04 · 深层嵌套 JSON 备份导致 App 崩溃（P1）

**位置**

- `app/src/main/java/com/wanglemai/app/domain/BackupCodec.kt:52-60`（`JSONObject(json)` 解析入口）
- `app/src/main/java/com/wanglemai/app/ui/settings/SettingsScreen.kt:81-105`（只捕获 `BackupException` / `IOException` / `Exception`，不捕 `Error`）

**现象**

在“从文件恢复”里选择一个 100KB、含 50,000 层嵌套数组的 JSON 文件后，App 立即崩溃退出：

```
FATAL EXCEPTION: main
Process: com.wanglemai.app, PID: 8882
java.lang.StackOverflowError: stack size 1035KB
    at org.json.JSONTokener.readArray(...)
    at org.json.JSONTokener.nextValue(...)
    ...
```

数据库未被修改（崩溃发生在预览解码阶段），但用户会话丢失，且没有得到“备份格式或版本不支持”的提示。按 AC23，非法/畸形备份应在预览前被拒绝且不能崩溃。

**复现步骤**

1. 生成 100,090 字节文件：`{"schemaVersion":1,"app":"com.wanglemai.app","exportedAt":1,"lists":[],"items":[],"junk":[[[…50000 层…]]]}`
2. `adb push` 到设备 Download。
3. 设置 → 从文件恢复 → 选择该文件。

**根因**

`org.json` 的 `JSONObject`/`JSONTokener` 递归解析嵌套结构，深度不受控；`BackupCodec.decode` 只捕获 `JSONException`，`SettingsScreen` 只捕获 `Exception`，`StackOverflowError` 直接逃逸到协程/线程顶层导致进程崩溃。

**正确修复后的效果**

- 畸形或超深备份被安全拒绝，显示现有错误文案（“备份格式或版本不支持”/“备份文件结构无效”）；
- App 不崩溃、不退出当前页面；
- 数据库保持不变；
- 有对应的回归测试（深层嵌套、超长数组等）保证后续不复发。

建议实现方向：在 `JSONObject` 解析前做一次线性扫描，限制最大嵌套深度（例如 64）和对象/数组数量；同时把 `StackOverflowError` 在 `BackupCodec.decode` 边界转换为 `BackupException` 作为兜底。

**证据**

- `test-results/crash-import-stackoverflow.log`
- 复现文件大小 100,090 字节（< 5MB 限制）

---

### BUG-05 · 更新安装包状态与“检查更新”结果不绑定（P3）

**位置**

- `app/src/main/java/com/wanglemai/app/data/update/UpdateCenter.kt:57-73`（`check()` 不重置/不关联 `download`）
- `app/src/main/java/com/wanglemai/app/ui/settings/SettingsScreen.kt:216-320`（`Ready` 安装入口挂在 `updateResult?.let` 内部）

**现象**

下载状态只保存 `file`、`sha256Verified`、`viaAcceleratedSource`，没有记录对应的版本。于是：

1. 用户下载 vX 后，隔一段时间检查到更新的 vY：
   - 页面显示“发现新版本：vY”和 vY 的 SHA256；
   - 但“安装”按钮实际安装的是之前下载的 vX 文件。
2. 用户下载 vX 后，在一次**失败的**检查更新中（例如断网）：
   - `checkError` 显示网络错误；
   - `updateResult` 变成 null，整个下载区（包含“安装”）不再渲染；
   - 已校验的 vX 安装包仍在磁盘上，但 UI 没有安装入口，只能重新下载。

**复现条件**

需要先处于“已下载（Ready）”状态。线上当前 v0.3.3 已是最新版本，无法直接构造“下载后出现更新版本”的时序，因此此项由代码路径确认；离线检查失败本身已实测（会清空 `updateResult`）。

**根因**

`Ready` 状态不携带版本/哈希，`SettingsScreen` 又只在整个 `updateResult` 非空时渲染下载区，导致“文件”和“检查结果”可以互相错配或一起消失。

**正确修复后的效果**

- 下载完成后记录对应版本（tag/versionName）和期望 SHA256；
- 只有当前检查结果与已下载版本一致时才显示“安装”；
- 检查到更新版本时，旧包被标记为“已过期”或清除，绝不出现“显示 vY、安装 vX”；
- 检查更新失败时，已下载且校验通过的包仍有独立可用的“安装 vX”入口，或至少不会被隐藏到无法操作。

**证据**

- 代码路径如上；无对应设备端复现（需要新版本发布或可注入版本号的构建）。

---

### BUG-06 · “重新下载”取消/失败会删除原有已验证安装包（P3）

**位置**

- `app/src/main/java/com/wanglemai/app/data/update/ApkDownloader.kt:84-85`
- `app/src/main/java/com/wanglemai/app/ui/settings/SettingsScreen.kt:301-307`

**现象**

在“安装包已下载并通过 SHA256 校验”状态下点击“重新下载”，下载器会在开始任何一次源尝试前删除 `filesDir/updates/` 下所有 `.apk`。如果用户随后取消下载，或者网络失败，原先已校验的安装包已经不存在，`Ready` 状态变为 `Idle/Failed`，“安装”入口消失，必须重新完整下载。

**复现步骤**

1. 处于已下载 Ready 状态（需新版本可用）。
2. 点“重新下载”。
3. 立即点“取消下载”，或断网等待失败。
4. 观察安装入口消失；`filesDir/updates/` 中原 APK 已被删除。

**根因**

新下载与旧文件的替换没有事务性：开始下载就清理目录，而不是在“新文件校验成功”后再替换。

**正确修复后的效果**

- 重新下载写入临时文件，SHA256 校验通过后再原子替换旧文件；
- 用户取消或下载失败时，原有已验证 APK 保留，“安装”入口继续可用；
- 不会因为一次失败的“重新下载”浪费已下载的流量。

**证据**

- 代码路径如上。

---

### BUG-07 · 导入备份可生成含换行的清单名（P3，低优先级）

**位置**

- `app/src/main/java/com/wanglemai/app/domain/Validation.kt:87-93`（`normalizeListName` 只校验空值和长度）
- 导入链路：`BackupCodec.decode` → `RoomBackupRepository.merge`

**现象**

正常 UI 的清单名输入框是单行，无法输入换行；但手工构造/修改的备份 JSON 中，清单名可以带 `\n` 并通过导入。导入后：

- 管理清单页的清单名显示成多行；
- 主页面下拉菜单出现两行名称；
- Widget 标题按单行截断，显示不完整；
- 布局与现有单行清单名规范不一致。

**复现方式**

构造备份：`"lists":[{"id":"<uuid>","kind":"TODO","name":"第一行\n第二行","isDefault":false,...}]`，导入后查看管理页/下拉菜单。

**根因**

`normalizeListName` 只做 trim + 长度校验，没有像标题校验那样拒绝换行和控制字符；单行输入控件掩盖了这个问题。

**正确修复后的效果**

- 清单名统一为单行：拒绝或规范化 `\r`/`\n`/控制字符；
- 非法备份在预览阶段返回明确错误，不写库；
- 管理页、下拉菜单和 Widget 标题的清单名排版保持一致。

**证据**

- JVM 探针结果：`Validation.normalizeListName("a\nb")` 返回 `a\nb`（未被拒绝）。
- 正常 UI 单行输入无法复现，仅导入路径可触发。

---

## 4. 低优先级观察（未计入 Bug 清单）

以下为代码审查发现的风险点，未在本次测试中复现为实际故障，建议后续加固时一并考虑：

1. **`WidgetUpdateCoordinator.syncInstances()` 在 `getAppWidgetIds` 返回空集合时会删除所有小部件配置。** 当前所有流程中该查询是权威的，但若 launcher/系统在启动早期短暂返回空，用户已配置的清单绑定、主题、密度会全部丢失。建议：查询结果为空时跳过清理，或只依据 `onDeleted` 清理。
2. **下载完成状态只保存在内存。** 若下载完成后进程被杀，`filesDir/updates/` 中的 APK 成为孤儿，重新打开设置无法安装；下次下载时才会被清理。可考虑持久化 Ready 的版本/路径或在启动时清理旧包。
3. **设置页导出/导入使用 `rememberCoroutineScope`。** 旋转屏幕会取消正在进行的导出，极端情况下留下不完整备份文件且无提示；可改用生命周期更长的 scope 或明确提示。
4. **`WidgetConfigureActivity.save()` 把 `refresh()` 异常也报告为“配置没有保存成功”。** 此时配置其实已提交，错误文案会误导用户重复保存；建议区分“已保存但刷新失败”。

---

## 5. 复核与排除

- 2026-11-07 报告中的 3 项（草稿竞态、排序溢出、Widget 错误残留）在本次全量测试和代码复核中仍未复现，结论与 `docs/bug-report-code-review-2026-11-07.md` 的复核一致，不计入本次清单。
- “大清单”Provider 目标格数经编译产物核对为 4×4（`widget_large_info.xml`），标准清单为 4×2，符合设计第 12 节，不是 Bug。
- 手工测试真实桌面小部件时曾出现一次“未完成，点此查看”，排查为**仪器测试直接调用 Repository 写库、绕过 `container.mutate` 的小部件刷新**，导致桌面保留旧版本 PendingIntent；重新启动 App（触发 `syncInstances` 刷新）后完成/撤销均正常。正常产品路径的所有写操作都会刷新小部件，因此不列为产品缺陷。
- 离线检查更新表现正常：显示“GitHub 与国内加速源均不可用，请检查网络后重试”，按钮恢复可用，无卡死。

---

## 6. 本次未覆盖的测试

- 小米/Redmi/POCO 真机与澎湃 OS 3 普通桌面、两排占格、壁纸合成、重启恢复；
- API 26 / 31 / 36 设备或模拟器；
- TalkBack 人工验收、键盘导航；
- 存储写满、IO 故障注入（AC25）；
- 下载过程中进程被杀/重启的恢复；
- 10,000 条记录下的性能目标（真机 release-like 构建）；
- 多用户/工作资料、分屏/画中画等特殊环境。

---

## 7. 证据索引

| 文件 | 内容 |
|---|---|
| `test-results/full-instrumentation-2026-11-09.log` | 43 项仪器测试完整日志（OK，43 tests） |
| `test-results/manual-undo-t2.png` / `t5` / `t7` | BUG-01 连续完成时撤销条时序 |
| `test-results/manual-editor-light.png` | BUG-02 系统浅色下的编辑页状态栏 |
| `test-results/manual-editor-light-app-dark-system.png` | BUG-02 App 浅色 + 系统深色 |
| `test-results/manual-settings-font2.png` | BUG-03 200% 字号主题行 |
| `test-results/ui-settings-font2.xml` | BUG-03 无障碍树（缺少“深色”） |
| `test-results/crash-import-stackoverflow.log` | BUG-04 崩溃日志 |
| `test-results/manual-update-check.png` | 在线检查更新（已是最新） |
| `test-results/manual-update-offline.png` | 离线检查更新错误提示 |
| `test-results/manual-import-result.png` | 导出→导入回环结果 |
| `test-results/manual-widget-undo.png` | 真实桌面小部件撤销后恢复标题 |

> `test-results/` 为仓库忽略目录，证据文件保留在本地，便于按需复查。

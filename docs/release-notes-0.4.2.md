# 忘了没 v0.4.2 · 自适应图标与桌面触点完善

2026-11-15。本版完成全新的自适应启动图标（含 Material You 单色主题图标层）、小部件空白区点按直达新增、小部件编辑器关闭后回到桌面三项改进，并统一全 App 的触控目标与控件尺寸令牌。

## 新增与变更

### 自适应启动图标
- 新增 `mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml`：深烘炭墨底 + 斜置叠层便签 + 朱砂索引耳，与 v0.4.0 Editorial 视觉体系同源；新增 `ic_launcher_monochrome` 单色层，Android 13+ 桌面支持 Material You 主题图标着色。
- Manifest 启动图标从 `@drawable/ic_app` 切换为 `@mipmap/ic_launcher` 与 `roundIcon`；`ic_app.xml` 重绘为与新图标同款的 48dp 矢量徽标（当前代码无引用，作为应用内素材保留）。minSdk 26，`anydpi-v26` 全覆盖，无需 PNG 回退。

### 小部件空白区点按新增
- 小部件根布局（`widget_root`）绑定"新增"PendingIntent：行间空隙、行下方留白与边缘等非交互区域点按等同"＋记一条"；圆形完成按钮、记录行、标题、页脚等既有触点自身消费点击、不冒泡到根布局。
- 无障碍描述同步为"空白处点按，新增…"；[产品文档](01-product.md#4-e01-快速编辑) 已同步该行为。

### 小部件编辑器关闭去向（独立任务）
- `QuickEntryActivity` 使用独立 taskAffinity（`com.wanglemai.app.editor`）；从小部件进入时 Intent 携带 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_TASK_ON_HOME`。入册保存、保存修订、撕下删除或收起后均回到桌面，不再把后台的 App 主界面带到前台；从 App 内进入编辑器仍回到调用处。

### EditorialMetrics 尺寸令牌与触控目标统一
- 新增共享令牌 `EditorialMetrics`（`Theme.kt`）：48dp 最小触控目标与主按钮、52dp 墨石悬浮岛、40/38dp 标准/紧凑控件、44dp 行内输入、56dp 行高、五档圆角与四档字号。
- 全部接入：主界面（索引耳、分段筛选、字面搜索框、记录行、悬浮岛）、快速录入（操作按钮/芯片/主按钮）、清单管理（行内"改名/删除"收敛为 `ListRowActionButton`，新增册输入）、设置页（主题芯片行距、清单入口 64dp、备份/更新按钮统一为 `EditorialActionButton`，替代原 `PaperActionBtn`）、小部件配置页与钉选控件。
- 按钮由固定 `height(46.dp)` 改为 `heightIn(min=…)` + `defaultMinSize`，200% 字体缩放下不再裁切文字。

## 验证

- **JVM 单元测试**：`72/72` 通过（`:app:testDebugUnitTest`）。
- **Android Lint**：`0 error`（`:app:lintDebug`）。
- **Android 仪器测试**：MuMu 模拟器（`LGE-AN10`，Android 15 / API 35）全量重跑 `53/53` 通过（`FrameworkUiTest` 17、`RepositoryTest` 23、`WidgetRenderingTest` 9、`WidgetPinTest` 3、`MigrationTest` 1），证据见 [android-instrumentation-0.4.2.txt](evidence/android-instrumentation-0.4.2.txt)。
- 新增仪器测试：`widgetEditorReturnsToLauncherAfterSaveAndDelete`（小部件编辑器新增保存/修订/删除后逐一断言回到桌面 Launcher 包）、空白区点按新增端到端断言、Widget 渲染正常与错误态根布局点击监听与内容描述断言。

## 构建

- `versionCode` 13，`versionName` `0.4.2`
- 安装包：`releases/wanglemai-0.4.2-debug.apk`（沿用既有 debug keystore 签名，支持覆盖安装升级；SHA256 见 [SHA256SUMS](../releases/SHA256SUMS.txt)）

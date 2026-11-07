# 暖米色 UI 开发说明

## 开发启动指令

请先读 `docs/visual-refresh/README.md`，打开 `prototype/warm/index.html` 体验各场景，再按本说明改造原生 Kotlin Compose 与 RemoteViews 页面。以 `overview.png` 的布局层级和下表 token 为设计基准。浏览器原型使用 CSS px，Android 间距以本说明 dp 为准、字体以 sp 为准。实现后运行现有 JVM 与设备测试，并补齐视觉验收截图。业务逻辑以当前 repository、ViewModel、更新与备份实现为依据。

工作区存在正在进行的更新下载等改动。开始修改前执行 `git status --short` 和 `git diff`，在现有改动上做最小视觉调整，提交时明确自身修改范围。

## 1. 设计 token

| 名称 | 浅色 | 暖深色 | 用途 |
| --- | --- | --- | --- |
| background | #F6F1E8 | #26221E | App 整体、系统栏 |
| surface | #FFFBF4 | #332D26 | 列表组、输入面板、设置分组 |
| surfaceVariant | #EDE4D7 | #43392F | 搜索底、分段轨道、轻按钮 |
| onSurface | #383129 | #F4EADC | 标题和正文 |
| onSurfaceVariant | #786B5D | #C0AF9B | 备注、辅助文案 |
| primary | #8A5335 | #E5B68E | 主要按钮、选中图标、操作文案 |
| onPrimary | #FFFAF2 | #34251B | 主按钮文字 |
| outlineVariant | #E3D8C8 | #514438 | 细分隔线 |
| error | #A33E32 | #FFB4A8 | 输入校验、保存失败 |
| widgetText | #383129 | #F4EADC | 桌面文字 |
| widgetSecondary | #6F6050 | #D3C2AC | 桌面次级文字 |
| widgetPrimary | #8A5335 | #E5B68E | 桌面新增与撤销 |
| widgetFill，原生基线 | #EBFFF8EA | #F0312920 | 92% 浅米色、94% 暖棕透色底 |
| widgetStroke | #C2FFFFFF | #30FFE9C9 | 1dp 高光边 |

`widgetFill` 是 Android #AARRGGBB。浏览器设计目标用 rgba(255,248,234,.72) + blur(24px) + saturate(115%)，暖深色 rgba(49,41,32,.78)。浏览器“原生近似”对应 .92/.94，设计目标和原生基线分别验收。XML alpha 四舍五入为 EB/F0。

将 Material3 的 `secondary` 映射 primary，`onSecondary` 映射 onPrimary，`primaryContainer`/`secondaryContainer` 映射 surfaceVariant，对应 onContainer 用 onSurface。`surfaceContainer*` 明确映射暖色阶，避免 NavigationBar、Surface tonalElevation 自动生成偏紫/偏绿底。关闭未明确使用的默认色调叠加，逐个检查 disabled、errorContainer 和 onErrorContainer。当前主题为显式配色，保持主题来源一致。

### 字体与尺寸

| 元素 | 字号/行高 | 字重 |
| --- | --- | --- |
| 页面标题 | 26/36sp | Medium 500 或系统等效 |
| 编辑面板标题 | 22/32sp | Medium |
| 清单正文 | 16/24sp | Regular 400 |
| 编辑主输入 | 20/30sp | Regular |
| 备注与辅助信息 | 12/18sp | Regular |
| 按钮、清单名称 | 14/20sp | Medium |
| 小部件标题 / 正文 / 数量 | 16/24、14/22、12/18sp | Medium / Regular / Regular |
| 底部导航标签 | 12/16sp | Medium |

系统字体优先，中文按系统字重映射。Web 展板缩放展示较小字号，原生以表格为准。所有 sp 跟随系统字体缩放。页面边距 20dp，分组间 20–24dp，行内间距 8–12dp。间距采用 4dp 网格。App 列表组圆角 20dp，搜索 14dp，按钮 15dp，编辑面板顶部 28dp，Widget 24dp，分段控件 11dp。

## 2. 页面与组件规格

### 2.1 主清单

- 顺序为品牌辅助行 → 主标题与数量说明 → 当前清单及管理入口 → 搜索 → 完成状态 → 内容组 → 新增 → 导航。
- 首行不放多余工具按钮，管理清单作为当前清单右侧文字动作，点击进入已有 ManageListsScreen。
- 当前清单仍为下拉选择，跨类型切换使用底部待办/待买入口。原型每类型仅一份清单，原生呈现已有自定义清单。
- 搜索高 48dp，浅填充，前置 search 图标。保留当前字面匹配、大小写、清单与状态过滤行为。
- 完成状态使用双项分段控件，高 40dp，选中项 surface + primary 文字。语义使用 selectableGroup 和 selected 描述。
- 单条至少 68dp，只有标题可收至 56dp。完成圆圈可见 20dp，点击区域至少 48×48dp，正文独立点击进入编辑。置顶用小 pin 矢量和“置顶”语义。标题最多 2 行，备注最多 1 行，数量独占右侧最多 64dp。
- 内容组 surface 实色，组内 1dp 低对比分隔线。LazyColumn 中保留稳定 item key，圆角由首尾行或组裁剪处理，长列表按需加载。
- 新增按钮整宽、高至少 48dp，固定在导航上方。列表底部 contentPadding 根据实际按钮与系统栏高度计算。
- 底部导航待办/待买/设置，各 48dp 以上触区，统一 24dp 线性图标与文字。选中焦糖色 + 小面积暖底。

### 2.2 编辑页

- 继续使用独立 QuickEntryActivity，顶部圆角面板，背景 28% 暖棕遮罩。键盘出现后底部保存区域保持可见，内容可滚动。
- 内容顺序为标题、单条/批量开关、目标清单、采购数量与单位、备注、置顶。原型仅演示单条，原生必须保留当前批量开关与 50 条上限、行号校验。
- 普通输入底色 background，边线 outlineVariant 1dp。聚焦使用 primary 2dp，错误使用 error 与文字，避免只换颜色。
- 沿用当前草稿恢复条、冲突提示、跨类型迁移确认、删除确认，以及“保存并继续”与“保存”两个操作。采购移至待办仍需确认清除数量与单位。
- 取消、返回和点遮罩沿用长期草稿保存策略。浏览器取消仅关闭演示面板，刷新会丢失会话数据。
- 保存中禁用提交及关闭、主按钮显示“保存中…”，失败保留输入，成功才清草稿。保存并继续保留当前清单并重新聚焦。

### 2.3 设置与管理

- 设置分为“外观与桌面”“清单与数据”“关于忘了没”，每组 surface 实色圆角 18dp。设置行至少 56dp，带副文案时至少 68dp。
- 应用主题完整保留 SYSTEM/LIGHT/DARK。原型仅提供浅/深演示，原生包括“跟随系统”。Widget 仍可独立设置主题。
- 两个系统 pin 入口、备份导入导出、更新检查/下载/进度/取消/失败重试/已下载安装/最新版本状态，使用相同颜色、字号和按钮层级。真实进度用已有下载状态，警示与错误保留文字。
- ManageListsScreen 用同样页面间距、分组面、文本与按钮。默认清单显示“默认”，编辑/删除自定义清单沿用当前确认和迁移流程。

### 2.4 小部件配置

- 顶部显示当前绑定清单的材质预览。清单、主题、密度各一行标签 + 控件，底部保存与取消。
- 标准和大号类型由当前配置 Intent 的 Provider 实例决定，原型“尺寸”控件只用于评审。原生显示类型名称，尺寸调整交给 Launcher。
- 系统主题以当前系统亮暗模式预览，显式浅/深色即时预览。舒适 52dp、紧凑 48dp 行高，两种模式的点击区均至少 48dp。
- 配置确认后调用现有保存与首帧刷新路径，取消沿用现有生命周期逻辑。

## 3. Widget 玻璃材质实现

### 基线实现

通过 `widget_background_light.xml` / `widget_background_dark.xml` 使用 layer-list + shape。底层用上表半透明实色与 24dp 圆角，描边 1dp。可增加 1dp 顶部高光，色值遵循 widgetStroke。背景效果与文字层分开，文字、图标、按钮始终 alpha=1。原生首版阴影预算为 0，Launcher 可自行处理外部阴影。网页目标的柔和阴影仅作为材质参考。

原生使用现有 RemoteViews 和允许的布局/视图。对当前 Android XML 方案，跨宿主可落地的基线是半透明面与高光，浏览器 backdrop-filter 的实时壁纸模糊超出该基线。增加 window blur、View.blur 或把整张 Widget 截图模糊都无法等价地产生 Launcher 壁纸的后景采样。若后续宿主提供专用能力，另行评估其适用范围。

复杂壁纸下先验证原生 92% 米色层。若任一基准壁纸上正文对比不足，将浅色层提升至 96% 或实色，记录设备与壁纸结果。不要通过降低文字透明度调节“玻璃感”。

### 布局与缩放

- 沿用标准目标 4×2、大号 4×4，参考面板标准约 320×220dp、大号约 320×340dp，最终使用宿主分配的尺寸。标题/页脚保持完整触点，正文从可用空间计算行数。
- 当前 `WidgetUpdateCoordinator` 使用 `(heightDp - 120) / rowHeight` 决定条数。调整视觉 padding 前先确认 120dp 预留是否覆盖新标题、页脚与内边距。舒适 rowHeight 基础 52dp、紧凑 48dp，跟随 fontScale。实际测量后再修正预留常量。
- 高度较小时减少条数，标准最多 3 条、大号最多 7 条，窄宽保留完成触区、标题和页脚，数量允许省略显示。超长标题单行省略，点正文可读全文。
- 字体放大到 200% 时减少行数并允许更高标题/页脚。极小尺寸空间不足以放 1 行时展示清单标题和“查看全部/记一条”入口，禁止强制塞入一行造成裁切。此项可能需要修正当前 `coerceIn(1, maxRows)`。
- 原型右侧为舒展的视觉展示，标准示意显示 2 行、大号显示 4 行。原生按空间计算实际条数。

### 动作与反馈

标题/查看全部打开绑定清单，正文进入编辑，圆圈直接完成，新增直接进入 QuickEntryActivity。保留每实例绑定、主题、密度与独立撤销。页脚优先级为冲突/错误 → 最近一次撤销 → 查看全部。原型把撤销集中在 App 的 5 秒反馈条，原生 Widget 沿用现有独立撤销页脚与有效期。

图标使用 VectorDrawable 或 ImageView 资源，替换字符 ○、✓ 的表现层时保持原来的 id 或同步修改所有 setTextViewText/setTextColor/点击绑定调用，验证 RemoteViews 反射方法与视图类型匹配。

## 4. 状态矩阵

| 场景 | 视觉与操作要求 |
| --- | --- |
| 初始读取 | 展示“正在读取…”，surface 空间稳定，不闪现空列表文案 |
| 待办/采购空态 | “清单空空，心里轻轻。” + 保留新增入口，采购保持采购语义 |
| 搜索无结果 | 展示没有找到相关记录，保留搜索与清空操作 |
| 完成/恢复 | 已完成划线 + 圆圈勾选，原生更新成功后给 5 秒 App 撤销；点击处理中禁用该行完成入口 |
| 草稿恢复 | 暖底提示条 + “丢弃”，输入恢复可编辑 |
| 保存失败 | 输入原样保留，按钮可重试，错误文字 aria/liveRegion 可读 |
| 编辑版本冲突 | 独立错误提示区，明确载入最新/保留草稿并覆盖两个动作 |
| 删除与跨类型迁移 | 同色系确认弹窗，破坏动作使用 error，文字说明后果 |
| Widget 读取失败 | “暂时无法读取 · 点此重试”，保留新增与重试 |
| 下载中/失败/可安装 | 进度、失败原因与动作明确，沿用 UpdateCenter 实际状态 |

## 5. 开发任务拆分

下表是交接任务划分，任务 1 合并后任务 2–4 可分别认领。多人修改共享文件时先协调。

| 任务 | 修改位置 | 完成条件 |
| --- | --- | --- |
| 1 主题与资源基线 | `ui/theme/Theme.kt`，新增必要 typography/shape token，`res/values/widget_colors.xml`，`res/values-night/colors.xml` | 浅/深两套 token，默认控件无异色，系统栏图标正确 |
| 2 App 清单与导航 | `ui/main/MainActivity.kt` | 待办/采购、搜索、清单选择、状态筛选、圆形完成、底部新增与导航对齐原型 |
| 3 编辑与配套页面 | `ui/editor/QuickEntryActivity.kt`、`ui/lists/ManageListsScreen.kt`、`ui/settings/SettingsScreen.kt` | 原有所有状态可达，键盘和大字号布局通过 |
| 4 Widget 与配置 | `widget/WidgetConfigureActivity.kt`、`widget/WidgetRenderer.kt`、`widget/WidgetUpdateCoordinator.kt`、`res/layout/widget_content.xml`、`widget_row.xml`、`widget_row_compact.xml`、`res/drawable/widget_background*.xml` | 原生近似材质、尺寸适配、独立主题与密度、所有 PendingIntent 动作正确 |
| 5 集成与视觉验收 | 既有 tests、androidTest 与新截图 | 下节检查通过，记录失败与真机待验项 |

上述 ui/widget 路径均相对 `app/src/main/java/com/wanglemai/app/`，res 路径相对 `app/src/main/`。首次实现可在 Theme.kt 内使用小规模私有 token，多个页面实际复用时再抽共享组件。

## 6. 验收与证据

### 可执行检查

```powershell
npm test
node prototype/warm/verify.cjs
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
# 先列出并明确选定设备，再执行现有设备测试流程。
adb devices
```

`npm test` 验证旧原型的业务基线。此次新提案的实际浏览器检查及截图见 `verification.json`，其验证范围是提案交互。原生实现后重新运行现有 `FrameworkUiTest`、`RepositoryTest`，因图标和布局调整失效的 UI 定位应以语义定位更新，保留业务断言。

### 视觉验收清单

- 360dp 与 412dp 宽 App，字号 100%、130%、200%。无文字遮挡、按钮截断、键盘覆盖保存操作，所有核心操作触点至少 48dp。
- 同一笔数据截图待办、采购、已完成、编辑（键盘开/关）、配置、设置、清单管理。各场景浅/深两套。
- Widget 标准/大号 × 舒适/紧凑 × 浅/深，覆盖最小 180×170dp 与常用尺寸。极小尺寸按空间折叠行数。
- 壁纸至少包含浅纯色、深纯色、明暗相间照片。文字保持可读，普通正文对比度目标 4.5∶1，大标题 3∶1，焦点与完成圆环 3∶1。
- TalkBack 可区分“完成某条”“编辑某条”“打开清单”，状态反馈可读；完成、已购、错误和选中状态同时有文字或形状提示。
- 多实例独立配置、完成撤销、刷新、旋转、重启/升级后的数据与绑定恢复，按现有测试范围重新验证。
- API26/31/34/36 与目标 HyperOS 真机分别记录实测情况。真机记录 Launcher 名称、系统版本、字体缩放、尺寸与壁纸；标明待验项目。

设计验收应同时打开本提案的“原生近似”模式作为材质参照，避免把浏览器实时模糊截图直接作为 RemoteViews 必须逐像素匹配的基准。

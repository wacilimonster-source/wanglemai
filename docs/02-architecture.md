# Android 技术架构与接口契约 v1.0

## 1. 决策基线

- Kotlin、单 app Gradle module 起步，按 package 分层；Compose Material3 负责 Activity；XML RemoteViews 负责 Widget。不要为原型引入 WebView。
- minSdk 26、compileSdk 36、targetSdk 36 作为已知实现基线；如发布渠道要求更高，先核验官方要求再统一调整。JDK17 或所选 AGP 官方要求的 JDK。首次搭建选择相互兼容的稳定 AGP/Kotlin/KSP/Compose/Room 版本，在 libs.versions.toml 固定并记录，不使用 `+` 或臆造版本。
- Room 是唯一业务事实来源；Coroutines/StateFlow；手动依赖注入 AppContainer；不引入网络层、Hilt、WorkManager 或服务端作为 MVP 必选依赖。
- MVP Provider 和 Activity 在应用默认进程；桌面宿主本来处于外部进程。不要把“桌面外部进程”与“小米专用 :widgetProvider”混淆。
- 仅申请 INTERNET 用于用户手动检查 GitHub Releases；不使用账号、云同步或后台网络服务。无麦克风、通知、精确闹钟、悬浮窗或广泛存储权限需求。备份通过 SAF 授权 URI 访问。

## 2. 建议目录

```text
app/src/main/java/com/wanglemai/app/
  WanglemaiApplication.kt   AppContainer.kt
  domain/model/            domain/validation/
  domain/ItemRepository.kt domain/BackupRepository.kt
  data/db/                 data/repository/  data/backup/
  ui/main/                 ui/editor/        ui/lists/  ui/settings/  ui/theme/
  widget/ListSmallProvider.kt   widget/ListLargeProvider.kt
  widget/WidgetActionReceiver.kt  widget/WidgetUpdateCoordinator.kt
  widget/WidgetRenderer.kt        widget/WidgetConfigureActivity.kt
app/src/main/res/layout/widget_small.xml, widget_large.xml
app/src/main/res/xml/widget_small_info.xml, widget_large_info.xml
app/src/main/res/values*/
app/src/test/               app/src/androidTest/
```

Provider 名称仅代表布局，实际可绑定 TODO 或 SHOPPING；使用 ListSmallProvider / ListLargeProvider，不按待办/待买另建 Provider。

## 3. 数据库 v1

所有时间均为 UTC epochMillis Long；显示本地时区。ID 为 UUID String（两个内置清单除外）。布尔值不使用中文文案持久化。枚举存稳定字符串。Unicode 限制统一使用 codePointCount，不用 Kotlin Char 数量代替。

| 表 | 字段与约束 |
|---|---|
| lists | id PK; kind TODO/SHOPPING; name; normalizedName; isDefault Boolean; createdAt; updatedAt; version Long>=1；UNIQUE(kind,normalizedName) |
| items | id PK; listId FK lists RESTRICT; title; note String默认空; quantityDecimal String?; unit String?; completed Boolean; pinned Boolean; sortKey Long; createdAt; updatedAt; completedAt Long?; deletedAt Long?; version Long>=1 |
| widget_configs | appWidgetId Int PK; providerClass String; listId FK; theme; density; updatedAt |
| widget_undo | appWidgetId PK/FK widget_configs CASCADE; commandId UUID UNIQUE; itemId FK; previousCompleted Boolean; previousCompletedAt Long?; expectedItemVersion Long |
| drafts | draftKey PK; payloadJson（含 targetListId、字段、batchMode）; baseItemVersion Long?; updatedAt |
| preferences | key PK; value String；主题、上次类型、每类上次清单 |
| applied_commands | commandId UUID PK; resultIdsJson; createdAt；防止新增及批量重试重复写入 |

索引：items(listId,deletedAt,completed,pinned,sortKey)、items(listId,completedAt)、widget_configs(listId)。default ID + isDefault 必须一致；每类恰有一个 default，初始化事务幂等。`completed=true` 要求 completedAt 非空，反之为空。TODO 记录 quantity/unit 必须为空。quantity 使用 BigDecimal 校验、toPlainString 存储，不能用 Float/Double。软删除项不参与 max sortKey 计算可接受，但排序仍有 id 决胜。迁移禁止 destructive fallback；导出 Room schema。

## 4. 业务接口（语义固定，具体 Kotlin 类型可按工程调整）

```kotlin
interface ItemRepository {
    fun observeItems(query: ItemQuery): Flow<List<Item>>
    suspend fun getItem(id: String): Item?
    suspend fun create(input: CreateItem, commandId: String): MutationResult
    suspend fun createBatch(input: BatchInput, commandId: String): MutationResult
    suspend fun update(id: String, expectedVersion: Long, input: EditItem): MutationResult
    suspend fun setCompleted(id: String, expectedVersion: Long, completed: Boolean,
                             originWidgetId: Int? = null): MutationResult
    suspend fun undoWidgetCompletion(widgetId: Int, commandId: String): MutationResult
    suspend fun undoCompletion(token: UndoToken): MutationResult // App内存撤销，同样执行CAS
    suspend fun delete(id: String, expectedVersion: Long): MutationResult
    suspend fun createList(kind: ListKind, name: String): MutationResult
    suspend fun renameList(id: String, expectedVersion: Long, name: String): MutationResult
    suspend fun deleteListAndMoveToDefault(id: String, expectedVersion: Long): MutationResult
}
```

`MutationResult`：Success(changedItemIds, affectedListIds, optionalUndoToken)、ValidationError(field,message,line?)、NotFound、Conflict(latestVersion?)、StorageError。取消协程继续抛 CancellationException，不包装成业务错误。Success 的数据用于显式刷新，禁止业务层引用 Activity/View。

并发规则：写入 `UPDATE ... WHERE id=:id AND version=:expectedVersion AND deletedAt IS NULL`，成功 version+1；受影响0行先判断 NotFound/Conflict。若 setCompleted 目标状态与最新状态已相同，返回无变更成功，不新增撤销、不递增版本。相反状态且版本过期则 Conflict。不要实现 toggle。

新增防重：调用方在一次提交产生一个 UUID commandId，按钮提交期间不可重复点击；失败重试沿用该 ID。增加 `applied_commands(commandId PK, resultIdsJson, createdAt)` 与新增事务同提交；重复 ID 返回原结果。批量一次命令对应多个 ID。命令表不进入备份，MVP 不定时清理。

完成事务：写 item → 清除该 item 的旧 widget_undo → 如来自 Widget 则给 originWidgetId 写新的撤销（previous state/time + 完成后的 version）→ commit。编辑、删除、移动、恢复都清除涉及 item 的 widget_undo。撤销使用 commandId 与 item version 双重比较，恢复 previous 状态及原 completedAt、version+1，并移除撤销；失败也移除陈旧入口。App Snackbar token 使用同样的 version CAS 语义，内存保存即可。

删除清单事务：验证非默认及版本 → 将全部 items（含软删除）迁移同类默认清单，移动项 version+1，sortKey 依创建顺序接到目标尾部 → 重绑 widget_configs → 清除相关撤销 → 删除/改绑引用该清单的 drafts（新建草稿迁到默认，编辑草稿保留字段但提示清单已改变）→ 删除清单 → commit。返回原/新清单 ID 和受影响 Widget。不得遗留外键悬空。

## 5. Activity 与路由

MainActivity: extras `list_id?`，校验存在后导航，失效则回默认清单并提示。QuickEntryActivity: extras `mode=create|edit`, `list_id?`, `item_id?`, `origin_widget_id?`。edit 以实际 item 的 listId 为准；不可把 Intent 中标题当作数据库内容。

WidgetConfigureActivity: 接收系统 EXTRA_APPWIDGET_ID，默认 RESULT_CANCELED；ID 无效直接退出；确认后持久化 config、生成首帧、返回 RESULT_OK 与同一 ID。支持系统重新配置；配置失败保持当前页。

QuickEntryActivity 用 Compose ModalBottomSheet 风格内容和透明/对话框窗口主题，IME 使用 adjustResize/Insets，真实键盘不得遮挡保存。用户点击 Widget 时直接 PendingIntent.getActivity；不得 Receiver/Service 跳转 Activity 形成 trampoline。onNewIntent 处理复用和不同记录切换，避免旧草稿覆盖新条目；最简单可每次创建独立编辑实例并保护重复提交。

所有内部动作组件按最小导出范围声明；Provider 根据系统要求声明权限/导出属性。PendingIntent 默认 FLAG_UPDATE_CURRENT|FLAG_IMMUTABLE，requestCode/data URI 包含 widgetId/action/itemId/version，避免实例串参。示例 URI：`wanglemai://widget/42/item/{uuid}/complete?v=3`。外部传入的记录 ID、版本、Widget ID 都必须校验。不要接受任意类名、URI 或未经校验的文件路径。

## 6. Widget 渲染与生命周期

WidgetRenderer 是纯“Snapshot → RemoteViews”转换，Snapshot 含实例配置、当前列表、totalActiveCount、undo、错误态；查询时计算 LIMIT，不能把整个数据库或大量 bitmap 发给桌面。使用标准 TextView/ImageView/Button/LinearLayout，完成圈用图标点击，兼容 API26，不依赖 API31 CheckBox。

更新入口：

1. App/编辑器写入成功：Coordinator 根据 affectedListIds 查询绑定的所有 Widget，重新读库并渲染。移动记录必须刷新旧/新清单。
2. WidgetActionReceiver：`goAsync()`，应用级 IO scope 执行短事务 + 更新，finally finish；不在 onReceive 主线程访问数据库。短链目标 <2秒，广播执行必须在平台超时前结束；无网络工作。
3. Provider.onUpdate/onAppWidgetOptionsChanged：读配置、按可用尺寸生成；onDeleted 清 config/undo，onRestored 用 oldIds/newIds 迁移实例配置并刷新。
4. 应用升级/系统要求更新/主题变化/进程重启后的首次操作：从数据库恢复；启动主应用时核对真实有效 widgetIds 与 configs，清孤儿并补渲染。
5. updatePeriodMillis=1800000 作为低频补偿，允许系统延迟，绝不依赖它做即时反馈。事件驱动更新才是主路径。

尺寸：两个 Provider 声明 targetCellWidth/Height（新 API）及兼容 minWidth/minHeight，resizeMode horizontal|vertical。依据 OPTION_APPWIDGET_MIN/MAX_WIDTH/HEIGHT 和新系统尺寸列表选择资源/可见行数；至少1行，标准最多3，大号最多7。字体缩放考虑实际 TextView 高度与可用空间；不得只除固定像素。

SYSTEM 主题用 values/values-night 资源；显式 LIGHT/DARK 使用独立资源方案。仍需真机验证宿主缓存和深色切换；不要仅依赖进程常驻时收到 configuration 回调。

MVP 进程内 Coordinator 用 Mutex 串行化同一实例生成更新，每轮都读最新 snapshot，防止慢的旧渲染覆盖新的状态。失败保留旧内容或明确错误页，记录不含用户正文的日志；下一次操作/系统更新重试。App 页面通过 Room Flow 更新。

## 7. 备份协议

```json
{
  "schemaVersion": 1,
  "app": "com.wanglemai.app",
  "exportedAt": 1793923200000,
  "lists": [{"id":"list-shopping-default","kind":"SHOPPING","name":"日常采购","isDefault":true,"createdAt":1793923200000,"updatedAt":1793923200000}],
  "items": [{"id":"550e8400-e29b-41d4-a716-446655440000","listId":"list-shopping-default","title":"牛奶","note":"","quantityDecimal":"2","unit":"盒","completed":false,"pinned":false,"sortKey":1024,"createdAt":1793923200000,"updatedAt":1793923200000,"completedAt":null}]
}
```

仅示例不是首次安装样例数据。备份不需要携带 version；新导入记录 version=1，已有 ID 跳过。导出必须包含两个默认清单（上例为节省篇幅只展示一个；实际导入验证默认清单对应关系，缺失默认清单可先本地幂等初始化）。外部 ID 允许两个内置 ID 或合法 UUID；重复 ID、同 ID 不同类型、非法 default 标记、未被导入或本机清单覆盖的 listId、违规字段、时间/排序非安全整数、未知枚举均整批拒绝。JSON 反序列化忽略无害未知字段，拒绝未知 schemaVersion。

导入前限制流大小，禁止先无界读入再检查。预览阶段不写库；确认时在事务中重新校验当前库冲突，防止预览后库改变。导入名称冲突按产品规则补后缀。撤销/config/drafts 不从文件导入。系统文件选择器取消不显示失败。备份含明文用户内容，界面告知“文件包含你的记录，请妥善保存”。

## 8. 测试与构建交付

单元：校验器、批量原始行号、字面搜索、排序、BigDecimal、备份冲突规则。Room instrumentation：事务原子性、重复 commandId、CAS 冲突、清单迁移、导入回滚、默认清单幂等。Compose：输入继承清单、草稿恢复、保存失败保留内容。Widget：真实 Launcher/小米桌面端到端与进程终止测试。

工程交付包括 Gradle Wrapper、版本目录、Room schema、测试命令、debug APK 路径、安装指导；不能把 HTML 原型交付当作原生实现完成。若 SDK/JDK 不可用，报告缺失路径和未运行项，不伪称构建成功。

## 9. 小米增强阶段（不混入 MVP）

小米专用 Provider/独立进程、曝光刷新、专属卡片中心上架作为单独变更。届时重新核验官方规范；多进程 Room 更新失效、应用初始化、跨进程刷新与内存预算需单独设计，不能直接将 android:process 改完就认为适配完成。

## 10. 官方依据（核对日期 2026-11-06）

- RemoteViews 支持范围：https://developer.android.com/reference/android/widget/RemoteViews
- Widget 更新与周期限制：https://developer.android.com/develop/ui/views/appwidgets/advanced
- Room：https://developer.android.com/training/data-storage/room
- 小米标准 Widget 与中心审核区分：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1588
- 小米技术规范（公开文档可能更新，增强阶段重新核验）：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584

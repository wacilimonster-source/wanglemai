# 忘了没 · 代码评审记录（2026-11-15）

- 评审范围：`app/src/main`（数据层、领域层、更新模块、Widget、UI）、CI 工作流、脚本
- 基线提交：`c2351aa`
- 记录方式：按发现顺序逐条追加，每条包含 位置 / 问题 / 影响 / 修复方案
- 严重级别：P1 = 数据丢失/崩溃/安全；P2 = 功能错误；P3 = 健壮性/可维护性

---

## 修复实施汇总

以下 `CR-01` ~ `CR-16` 全部已完成代码修复、补充回归测试并完成全量复测验证（JVM 单元测试 `72/72`、`Lint` 0 error、MuMu Android 15 仪器测试 `52/52`、`prototype/warm/verify.cjs` `30/30`、`npm test` `30/30`）：

| 编号 | 级别 | 修复文件 | 核心改动 |
| --- | --- | --- | --- |
| CR-01 | P3 | `AppDatabase.kt`、`RoomItemRepository.kt` | 新增 `pruneCommands(cutoff)`，`create`/`createBatch` 时清理超过 7 天的幂等命令记录 |
| CR-02 | P3 | `WanglemaiApplication.kt` | `AppContainer.mutate` 与 `onConfigurationChanged` 改为显式重抛 `CancellationException` |
| CR-03 | P3 | `RoomBackupRepository.kt` | `merge()` 按 `(sortKey, createdAt, id)` 稳定排序并从目标清单 `maxSortKey` 后追加 |
| CR-04 | P3 | `ApkDownloader.kt`、`UpdateCenter.kt` | `UpdateCenter` 初始化与每次下载前清理非活跃 `.part` 残留临时文件 |
| CR-05 | P3 | `UpdateChecker.kt`、`ApkDownloader.kt` | `UpdateException` 增加 `isIntegrityError` 标志，保留校验失败而不被备用源网络错误掩盖 |
| CR-06 | P3 | `UpdateCenter.kt` | 维护 `checkJob`（`CoroutineStart.LAZY`），新检查取消旧检查且仅最新任务写状态 |
| CR-07 | P2 | `WanglemaiApplication.kt`、`ListProviders.kt`、`WidgetPinCoordinator.kt` | 应用全局 `scope` 安装 `CoroutineExceptionHandler`；`onRestored` 增加异常捕获与 `syncInstances()` 自愈；`findNewWidgetLocked()` 纳入 `try` 保护 |
| CR-08 | P3 | `WidgetUpdateCoordinator.kt` | `restore()` 中的 `transientMessages` 清理并入 `mutex.withLock` 保护 |
| CR-09 | P3 | `WidgetConfigureActivity.kt` | 保存配置事务成功后将 `widgets.refresh(widgetId)` 与保存状态解耦，宿主刷新异常不破坏已保存结果 |
| CR-10 | P2 | `MainActivity.kt` | 恢复完成操作显示 `"已恢复为待办"`，完成操作显示 `"已归档"`，并通过显式 5 秒计时器关闭撤销 Snackbar |
| CR-11 | P3 | `MainActivity.kt` | `onCreate` 仅在 `savedInstanceState == null` 时消费启动 Intent，并在消费后移除 `"list_id"` extra |
| CR-12 | P3 | `MainViewModel.kt`、`EditorViewModel.kt` | 补齐偏好写入、草稿持久化及失败后回读 `getItem`/`list` 的异常保护（均重抛 `CancellationException`） |
| CR-13 | P3 | `AppDatabase.kt`、`RoomItemRepository.kt` | `AppDao.edit` 支持更新 `sortKey`，跨清单移动时分配目标清单末尾 `sortKey` |
| CR-14 | P2 | `SettingsScreen.kt` | `writeText` 优先使用 `"wt"` 截断写入，不支持时回退默认模式，避免覆盖旧备份尾部残留字节 |
| CR-15 | P3 | `BackupCodec.kt`、`SettingsScreen.kt` | 在 `BackupCodec.decode` 与 `readBoundedText` 入口剥离 UTF-8 BOM `\uFEFF`，移除死代码 `TOO_LARGE` 分支 |
| CR-16 | P2 | `ManageListsScreen.kt` | 为 `LaunchedEffect(renamingId/deletingId, lists, submitting)` 增加 `!submitting` 门控，防止提交窗口内误报"记录已不存在" |

---

## 问题清单


### CR-01 · `applied_commands` 表只增不减，无清理机制（P3）

- **位置**：[RoomItemRepository.kt](../app/src/main/java/com/wanglemai/app/data/RoomItemRepository.kt#L77) / [RoomItemRepository.kt#L96](../app/src/main/java/com/wanglemai/app/data/RoomItemRepository.kt#L96)、[AppDatabase.kt#L53-L54](../app/src/main/java/com/wanglemai/app/data/db/AppDatabase.kt#L53-L54)
- **问题**：每次 `create` / `createBatch` 都会向 `applied_commands` 写一行幂等记录，但 DAO 中没有任何删除语句，App 生命周期内该表无限增长。
- **影响**：长期使用后数据库体积膨胀；备份不导出该表，因此也无法通过"导出-重装-导入"自然清理。
- **修复方案**：
  1. DAO 增加 `@Query("DELETE FROM applied_commands WHERE createdAt < :before") suspend fun pruneCommands(before: Long)`；
  2. 在 `initialize()`（或每日首次 `mutate`）中调用，保留窗口取 7 天即可（幂等只需覆盖进程重启/重复提交的短窗口）；
  3. 在 `RepositoryTest` 中补充"过期命令被清理、窗口内命令仍可重放"的用例。

### CR-02 · `AppContainer.mutate` / `onConfigurationChanged` 用 `runCatching` 吞掉 `CancellationException`（P3）

- **位置**：[WanglemaiApplication.kt#L26](../app/src/main/java/com/wanglemai/app/WanglemaiApplication.kt#L26)、[WanglemaiApplication.kt#L44](../app/src/main/java/com/wanglemai/app/WanglemaiApplication.kt#L44)
- **问题**：`runCatching { widgets.refreshAll() }` 会捕获 `CancellationException`，调用方协程被取消（如 ViewModel 销毁）时取消信号被吞掉，协程继续执行后续代码。
- **影响**：违反结构化并发约定；取消后的协程仍会返回结果并可能更新已销毁的 UI 状态。仓库层 `mutate` 已正确地重新抛出 `CancellationException`，此处不一致。
- **修复方案**：抽取一个工具函数并全局替换：
  ```kotlin
  inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
      Result.success(block())
  } catch (e: CancellationException) { throw e } catch (e: Throwable) { Result.failure(e) }
  ```
  `AppContainer.mutate` 中改用该函数；`onConfigurationChanged` 中的 `scope.launch` 属于顶层任务，可保留但同样建议统一。

### CR-03 · 备份合并时原样保留 `sortKey`，并入已有清单的记录不会"追加到末尾"（P3）

- **位置**：[RoomBackupRepository.kt#L120-L140](../app/src/main/java/com/wanglemai/app/data/RoomBackupRepository.kt#L120-L140)
- **问题**：导入记录直接使用备份中的 `sortKey`。两类记录会与本地记录交错排序：
  1. 旧版 SHOPPING 清单的记录被改写到 `DEFAULT_TODO`；
  2. 所属清单在本地已存在（同 id 被跳过）的记录。

  而设备端迁移 `MIGRATION_1_2`（`mergeShoppingItems`）与"删除清单"都是**追加到末尾**，两条路径行为不一致——`legacyShoppingListIds` 的注释还声称 "matching the on-device v1->v2 migration"。
- **影响**：导入后，旧备份里的记录会随机插到本地待办中间，用户不容易找到刚导入的内容。
- **修复方案**：在 `merge()` 中按目标清单维护一个 `nextSortKey` 游标：
  ```kotlin
  val cursors = HashMap<String, Long>()
  fun allocate(listId: String) = cursors.getOrPut(listId) { dao.maxSortKey(listId) }
      .plus(1024).also { cursors[listId] = it }
  // listId 被改写，或者目标清单不是本次新建的清单：
  val sortKey = if (rewritten || listId !in newlyInsertedListIds) allocate(listId) else item.sortKey
  ```
  在 `BackupCodecTest`/`RepositoryTest` 中补充"旧版购物记录导入后排在待办末尾"的用例。

### CR-04 · 进程被杀后残留的 `.part` 临时文件永远不会被清理（P3）

- **位置**：[ApkDownloader.kt#L91](../app/src/main/java/com/wanglemai/app/data/update/ApkDownloader.kt#L91)、[ApkDownloader.kt#L130-L132](../app/src/main/java/com/wanglemai/app/data/update/ApkDownloader.kt#L130-L132)
- **问题**：临时文件名为 `<name>.apk.<uuid>.part`，只在本次下载的 `catch` 中删除。下载过程中进程被系统回收或被强杀时，`catch/finally` 不会执行，残留文件不再被任何逻辑引用；成功后的清理只删除 `*.apk`，不会处理 `.part`。
- **影响**：`filesDir/updates` 中会积累数个 APK 大小的垃圾文件，占用用户存储，且只能通过"清除数据"清理。
- **修复方案**：在 `downloadFrom` 开始写入前（或 `UpdateCenter` 初始化时）清理不属于当前任务的过期临时文件：
  ```kotlin
  targetDir.listFiles()?.filter { it.isFile && it.name.endsWith(".part") && it != temp }
      ?.forEach { it.delete() }
  ```
  由于 `UpdateCenter.downloadApk` 会先 `cancel` 旧任务，同一时刻只有一个有效下载；为了避免与"正在收尾的旧任务"竞争，也可以只删除 `lastModified` 早于 10 分钟的 `.part` 文件。

### CR-05 · 依赖错误文案字符串判断错误类型（P3）

- **位置**：[ApkDownloader.kt#L70](../app/src/main/java/com/wanglemai/app/data/update/ApkDownloader.kt#L70)
- **问题**：`!last.message.contains("校验失败")` 通过中文提示文案判断是否为 SHA256 校验失败。文案一旦调整（如做国际化、改成"校验不通过"），聚合逻辑就会悄悄失效，把校验失败误报成"网络失败"。
- **影响**：用户被引导去"检查网络"，而真正的问题是安装包被篡改或损坏，安全相关提示被掩盖。
- **修复方案**：给 `UpdateException` 增加类型字段或使用子类：
  ```kotlin
  class UpdateException(override val message: String, val kind: Kind = Kind.GENERIC) : Exception(message) {
      enum class Kind { GENERIC, NETWORK, INTEGRITY }
  }
  // 抛出： throw UpdateException("安装包校验失败，已删除", UpdateException.Kind.INTEGRITY)
  // 判断： if (sources.size > 1 && last.kind != UpdateException.Kind.INTEGRITY) ...
  ```
  并在 `ApkDownloaderTest` 中直接断言 `kind`，不再断言文案。

### CR-06 · `UpdateCenter.check()` 并发调用存在结果覆盖竞态（P3）

- **位置**：[UpdateCenter.kt#L66-L84](../app/src/main/java/com/wanglemai/app/data/update/UpdateCenter.kt#L66-L84)
- **问题**：每次 `check()` 都直接 `scope.launch` 一个新协程，旧的检查任务不会被取消。连续触发两次检查时（如快速双击、Activity 重建后自动检查），可能出现：
  1. 先发起、后返回的请求覆盖较新的 `result` / `checkError`；
  2. 先结束的那个协程在 `finally` 中把 `checking` 置为 `false`，另一个请求实际上还在进行。
- **影响**：UI 状态错乱（显示过期结果，或者"检查中"提前消失后结果才出现）。
- **修复方案**：与 `downloadJob` 一样持有 `checkJob`，新检查开始前取消旧任务，并且只允许当前任务写状态：
  ```kotlin
  @Volatile private var checkJob: Job? = null
  fun check(currentVersion: String) {
      checkJob?.cancel(); downloadJob?.cancel()
      ...
      val job = scope.launch(start = CoroutineStart.LAZY) {
          val self = coroutineContext[Job]
          try { ... if (checkJob === self) result.value = checked }
          finally { if (checkJob === self) checking.value = false }
      }
      checkJob = job; job.start()
  }
  ```

### CR-07 · 应用级 `scope` 没有 `CoroutineExceptionHandler`，`onRestored` 等路径的未捕获异常会直接让进程崩溃（P2）

- **位置**：[WanglemaiApplication.kt#L19](../app/src/main/java/com/wanglemai/app/WanglemaiApplication.kt#L19)、[ListProviders.kt#L45-L54](../app/src/main/java/com/wanglemai/app/widget/ListProviders.kt#L45-L54)、[WidgetPinCoordinator.kt#L66-L71](../app/src/main/java/com/wanglemai/app/widget/WidgetPinCoordinator.kt#L66-L71)
- **问题**：`CoroutineScope(SupervisorJob() + Dispatchers.IO)` 没有安装异常处理器。在 Android 上，`launch` 中未被捕获的异常会交给线程的默认 `UncaughtExceptionHandler`，**进程直接崩溃**。目前存在未兜底的路径：
  - `BaseListProvider.onRestored` → `widgets.restore(...)` 只有 `try/finally`，没有 `catch`，数据库异常会让备份恢复流程把 App 弄崩；
  - `WidgetPinCoordinator.request` 中 `findNewWidgetLocked()`（第 70 行）位于 `try` 之外，`AppWidgetManager` 抛出 `RuntimeException`（宿主进程死亡等）时会崩溃。
- **影响**：后台广播（系统恢复 Widget、钉选回调）触发崩溃，用户看到"应用已停止"，并且可能进入崩溃循环。
- **修复方案**：
  1. 应用 scope 增加兜底处理器，统一记录日志：
     ```kotlin
     private val crashGuard = CoroutineExceptionHandler { _, e -> Log.e("AppScope", "Uncaught", e) }
     val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)
     ```
  2. `onRestored` 中补上 `catch (e: Exception) { Log.w(...) }`，失败后执行 `syncInstances()` 做自愈；
  3. `request()` 中把 `findNewWidgetLocked()` 移到 `try` 块内。

### CR-08 · `WidgetUpdateCoordinator.transientMessages` 在锁外被并发修改（P3）

- **位置**：[WidgetUpdateCoordinator.kt#L30](../app/src/main/java/com/wanglemai/app/widget/WidgetUpdateCoordinator.kt#L30)、[WidgetUpdateCoordinator.kt#L70](../app/src/main/java/com/wanglemai/app/widget/WidgetUpdateCoordinator.kt#L70)
- **问题**：`transientMessages` 是非线程安全的 `LinkedHashMap`，其他读写都在 `mutex.withLock` 内，唯独 `restore()` 第 70 行 `oldIds.forEach { transientMessages.remove(it) }` 在锁外执行。该协程运行在 `Dispatchers.IO` 多线程池中，可能与 `showMessage/refresh` 同时修改 Map。
- **影响**：小概率出现 `ConcurrentModificationException`，或 Map 内部状态损坏（结合 CR-07 会导致崩溃）。
- **修复方案**：把第 70 行放进 `mutex.withLock { ... }`；或者直接改为 `ConcurrentHashMap<Int, String>()`，两种方式任选其一，推荐前者以保持"所有渲染状态都由 mutex 保护"的一致约定。

### CR-09 · Widget 配置页：配置已保存但刷新失败时，会被当作"保存失败"而取消放置（P3）

- **位置**：[WidgetConfigureActivity.kt#L119-L149](../app/src/main/java/com/wanglemai/app/widget/WidgetConfigureActivity.kt#L119-L149)
- **问题**：`saveConfig` 事务提交后紧接着在同一个 `try` 中调用 `container.widgets.refresh(widgetId)`。如果刷新抛出异常（如 `RemoteViews` 超过 Binder 限制、宿主异常），会进入 `catch`，提示"配置没有保存成功"，并且不会 `setResult(RESULT_OK)`。用户若随后返回，Activity 的结果仍是 `RESULT_CANCELED`，首次放置的 Widget 会被桌面移除，而数据库中却留下了这条配置。这与 `AppContainer.mutate` 中"宿主刷新错误不得把已提交的保存变成失败"的约定相矛盾。
- **影响**：提示与实际状态不一致；残留孤儿配置（要等下一次 `syncInstances` 才会清理）。
- **修复方案**：把刷新与保存解耦：
  ```kotlin
  withContext(Dispatchers.IO) {
      container.db.withTransaction { ... saveConfig(...) }
      runCatching { container.widgets.refresh(widgetId) }   // 刷新失败不影响结果
  }
  setResult(RESULT_OK, ...)
  ```
  刷新失败时可以退而求其次调用 `widgets.showError(widgetId)`，由 Widget 上的"重试"入口自愈。

### CR-10 · 从"已完成"恢复记录时，Snackbar 仍提示"已归档"（P2）

- **位置**：[MainActivity.kt#L192-L215](../app/src/main/java/com/wanglemai/app/ui/main/MainActivity.kt#L192-L215)
- **问题**：`onToggle` 对**完成**和**恢复**两种方向都调用 `vm.setCompleted(item)`（内部取反 `!item.completed`），仓库在两个方向上都会返回 `UndoToken`，但 UI 一律显示 `message = "已归档"`。在"已完成"筛选下点按恢复一条记录，用户看到的却是"已归档 · 撤销"，语义正好相反。另外，产品文档 [01-product.md#L42](01-product.md) 约定撤销条为 **5 秒**，而 `SnackbarDuration.Short` 约为 4 秒。
- **影响**：误导用户，可能让用户误以为操作失败而再次点击"撤销"，反向抵消刚才的恢复。
- **修复方案**：
  ```kotlin
  val message = if (item.completed) "已恢复" else "已归档"
  val action = snackbar.showSnackbar(message = message, actionLabel = "撤销",
      duration = SnackbarDuration.Indefinite /* 外层配合 withTimeoutOrNull(5_000) 关闭 */)
  ```
  或者保留 `Short`，但同步修改产品文档中的 5 秒约定；并在 `FrameworkUiTest` 中补充"恢复时提示文案"的断言。

### CR-11 · `MainActivity` 重建时重复处理启动 Intent，用户切换过的清单会被重置（P3）

- **位置**：[MainActivity.kt#L61-L90](../app/src/main/java/com/wanglemai/app/ui/main/MainActivity.kt#L61-L90)
- **问题**：`onCreate` 无条件调用 `handleIntent(intent)`。从 Widget 的"查看全部"进入（Intent 带 `list_id`）后，用户切换到其他清单，此时如果发生配置变更（旋转、深色模式切换、字号/语言变化、分屏），Activity 重建时会再次执行 `vm.openList(widgetListId)`，把用户切回 Widget 绑定的清单，同时清空搜索词和"已完成"筛选。
- **影响**：用户的浏览状态被意外重置。
- **修复方案**：只在首次创建时消费 Intent，并在消费后移除 extra：
  ```kotlin
  if (savedInstanceState == null) handleIntent(intent)

  private fun handleIntent(intent: Intent?) {
      val id = intent?.getStringExtra("list_id") ?: return
      intent.removeExtra("list_id")
      lifecycleScope.launch { vm.openList(id) }
  }
  ```

### CR-12 · UI 协程中有多处数据库调用没有兜底，异常会导致崩溃或界面卡在 busy 状态（P3）

- **位置**：
  - [MainViewModel.kt#L99-L106](../app/src/main/java/com/wanglemai/app/ui/main/MainViewModel.kt#L99-L106) `selectList` 中的 `repo.setPreference`；
  - [MainViewModel.kt#L153-L160](../app/src/main/java/com/wanglemai/app/ui/main/MainViewModel.kt#L153-L160) `deleteList` 中的 `repo.setPreference`；
  - [EditorViewModel.kt#L290-L292](../app/src/main/java/com/wanglemai/app/ui/editor/EditorViewModel.kt#L290-L292)、[#L323](../app/src/main/java/com/wanglemai/app/ui/editor/EditorViewModel.kt#L323)、[#L340](../app/src/main/java/com/wanglemai/app/ui/editor/EditorViewModel.kt#L340)、[#L397](../app/src/main/java/com/wanglemai/app/ui/editor/EditorViewModel.kt#L397) 中的 `repo.getItem`；
  - [EditorViewModel.kt#L223-L226](../app/src/main/java/com/wanglemai/app/ui/editor/EditorViewModel.kt#L223-L226) `writeDraft` 中的 `repo.saveDraft`（由 `scheduleDraft` 在 `viewModelScope` 中调用，没有 `runCatching`）。
- **问题**：写操作都经过 `RoomItemRepository.mutate` 统一兜底，但上述读操作和偏好/草稿写入直接抛出异常。它们运行在 `viewModelScope`、`rememberCoroutineScope` 或 `lifecycleScope` 中，未捕获的异常会让 App 崩溃；`performSave/delete` 失败分支中如果 `getItem` 抛出异常，`busy=true` 也永远不会被复位。
- **影响**：磁盘已满或数据库被锁等场景下直接崩溃，而不是给出提示。
- **修复方案**：
  - `scheduleDraft` 改为 `runCatching { writeDraft(...) }`，与 `flushDraft` 保持一致；
  - `performSave/delete` 失败分支中改为 `runCatching { repo.getItem(id) }.getOrNull()`，失败时只复位 `busy` 并显示通用错误；
  - `selectList/deleteList` 中的 `setPreference` 改为 `runCatching`（偏好写入失败不应影响切换清单）；
  - 也可以在仓库层增加 `safeRead { }` 包装，把异常转换为可展示的结果类型。

### CR-13 · 编辑记录并更换清单时沿用原 `sortKey`，记录在新清单中的位置不确定（P3）

- **位置**：[RoomItemRepository.kt#L100-L111](../app/src/main/java/com/wanglemai/app/data/RoomItemRepository.kt#L100-L111)、[AppDatabase.kt#L33-L35](../app/src/main/java/com/wanglemai/app/data/db/AppDatabase.kt#L33-L35)
- **问题**：`dao.edit(...)` 会修改 `listId`，但保留旧清单里分配的 `sortKey`。`sortKey` 是在各清单内独立递增的，因此记录移入新清单后可能插到中间、顶部，或者与已有记录的 `sortKey` 相同（只能靠 `createdAt/id` 决定顺序）。"删除清单"、迁移和备份并入都采用"追加到目标清单末尾"，行为不一致。
- **影响**：用户把记录移到另一个清单后找不到它的位置；排序结果不稳定。
- **修复方案**：在 `update()` 中判断是否跨清单：
  ```kotlin
  val sortKey = if (normalized.listId != old.listId) nextSortKey(normalized.listId) else old.sortKey
  ```
  并把 `edit` 查询改为同时写入 `sortKey=:sortKey`；跨清单移动时，同时调用 `clearItemUndo`（目前已经调用）。补充 `RepositoryTest`："移动后的记录排在目标清单末尾"。

### CR-14 · 导出备份时 `openOutputStream(uri)` 默认 `"w"` 模式可能不截断旧内容，导致备份 JSON 损坏（P2）

- **位置**：[SettingsScreen.kt#L711-L716](../app/src/main/java/com/wanglemai/app/ui/settings/SettingsScreen.kt#L711-L716)
- **问题**：`contentResolver.openOutputStream(uri)` 等价于 `openOutputStream(uri, "w")`。在 Android 10 及以上版本中，部分 `DocumentsProvider`（包括系统 `ExternalStorageProvider` 和若干云盘）对 `"w"` **不执行截断**。用户在文件选择器中选择覆盖一个更大的旧备份时，新内容写完后旧文件尾部的字节仍然保留，结果得到一个无法解析的 JSON。
- **影响**：导出时提示"备份已导出"，真正需要恢复时却发现备份不可用，属于静默的数据丢失风险。
- **修复方案**：显式使用截断模式，并在写完后做一次回读校验：
  ```kotlin
  val output = context.contentResolver.openOutputStream(uri, "wt")
      ?: throw IOException("无法写入文件")
  output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
  // 可选：readBoundedText(context, uri, BACKUP_MAX_BYTES) 后再执行 BackupCodec.decode 做自检
  ```
  `"wt"` 不被支持时，会抛出 `FileNotFoundException/IllegalArgumentException`，此时可回退到 `"w"`，并提示用户"请选择新文件"。

### CR-15 · 导入时不兼容带 BOM 的 UTF-8 文件；`TOO_LARGE` 分支是死代码（P3）

- **位置**：[SettingsScreen.kt#L692-L709](../app/src/main/java/com/wanglemai/app/ui/settings/SettingsScreen.kt#L692-L709)、[SettingsScreen.kt#L108-L109](../app/src/main/java/com/wanglemai/app/ui/settings/SettingsScreen.kt#L108-L109)
- **问题**：
  1. `String(bytes, UTF_8)` 会保留开头的 `\uFEFF`。用户用 Windows 记事本等工具编辑或另存备份后，文件通常带 BOM，`org.json` 的 `JSONTokener` 不会把 `\uFEFF` 当作空白跳过，导致被判定为"备份格式或版本不支持"。
  2. `readBoundedText` 超限时抛出的是 `BackupException`，`catch (e: IOException)` 中 `e.message == "TOO_LARGE"` 的分支永远不会命中。
- **影响**：合法备份被拒绝，用户无从排查；死代码会误导后续维护。
- **修复方案**：
  ```kotlin
  String(output.toByteArray(), Charsets.UTF_8).removePrefix("\uFEFF")
  ```
  （或者在 `BackupCodec.decode` 入口统一 `removePrefix`，这样单元测试也能覆盖）；删除 `TOO_LARGE` 判断，`IOException` 分支统一提示"读取文件失败"。

### CR-16 · 删除清单成功后会误弹"记录已不存在，请重新查看"（P2）

- **位置**：[ManageListsScreen.kt#L71-L76](../app/src/main/java/com/wanglemai/app/ui/lists/ManageListsScreen.kt#L71-L76)、[ManageListsScreen.kt#L536-L555](../app/src/main/java/com/wanglemai/app/ui/lists/ManageListsScreen.kt#L536-L555)
- **问题**：`LaunchedEffect(deletingId, lists)` 用来检测"正在删除的清单被外部删除"。但删除流程是：`onDelete` → `container.mutate`（事务提交后，还要同步执行 `widgets.refreshAll()`，耗时较长）→ 返回结果后才执行 `deletingId = null`。事务提交后，Room 的 `observeLists()` 会立即发出不含该清单的新列表，重组时 `deleting == null && deletingId != null` 成立，于是先触发 `onMessage("记录已不存在，请重新查看")`，随后成功分支再弹出"已删除清单……"。
- **影响**：每次正常删除都可能先出现一条错误提示（Snackbar 排队显示），删除确认对话框也会在操作完成前提前消失。
- **修复方案**：在提交期间屏蔽该检测，并在发起删除时记录"本地发起的删除"：
  ```kotlin
  LaunchedEffect(deletingId, lists, submitting) {
      if (!submitting && deletingId != null && deleting == null) { ... }
  }
  ```
  或者在点击"删除并移动"时就先 `val target = deleting; deletingId = null`，用局部变量 `target` 完成后续流程（对话框改为由单独的 `deleteInFlight` 状态控制）。`renamingId` 的检测逻辑同理，建议一起加上 `!submitting` 条件。


# 忘了没 v0.4.1 · 代码评审修复与稳定性增强

2026-11-15。本版为 `v0.4.0` 发布后的首次全面代码评审修复与稳定性加固版本，共修复 `CR-01` ~ `CR-16` 全部 16 项问题并补充自动化回归测试，同时补足中小屏配置页与中文桌面测试适配。

## 修复摘要（CR-01 ~ CR-16）

### 数据、迁移与备份可靠性
- **导出防残留损坏（CR-14）**：导出 JSON 备份时优先采用 `"wt"` 截断模式写入（不支持时回退默认模式），防止覆盖更大旧备份文件时尾部残留旧字节导致 JSON 损坏。
- **导入 UTF-8 BOM 兼容（CR-15）**：`BackupCodec.decode` 与设置页有界读取统一剥离 `\uFEFF` BOM 前缀，并移除死代码分支。
- **备份并入与跨清单排序（CR-03 / CR-13）**：备份合并及跨清单编辑移动记录时统一从目标清单当前 `maxSortKey` 后顺延追加，消除新旧记录交错乱序。
- **幂等命令表定期清理（CR-01）**：新增 `AppDao.pruneCommands`，在新增/批量写入时自动清理 7 天前的 `applied_commands` 历史记录。

### 协程、Widget 与页面交互健壮性
- **崩溃兜底与取消传播（CR-02 / CR-07 / CR-12）**：为应用级 `scope` 安装 `CoroutineExceptionHandler`；`AppContainer.mutate`、`onConfigurationChanged`、Widget 恢复/钉选与主界面/编辑器数据库调用统一保留 `CancellationException` 传播并提供安全容错。
- **恢复待办撤销反馈与 5 秒定时（CR-10）**：在“已完成”标签页将记录恢复为未完成时，Snackbar 文案由错误的“已归档”修正为“已恢复为待办”，并配齐 5 秒自动消失计时。
- **清单状态与旋转保活（CR-11 / CR-16）**：`MainActivity` 仅首次启动消费 `list_id` Intent Extra，配置变更重建后不再重置当前选中分册；清单管理页删除/改名提交窗口期间屏蔽“记录已不存在”误报。
- **Widget 协调器与配置解耦（CR-08 / CR-09）**：`WidgetUpdateCoordinator.restore()` 的临时提示词清理纳入互斥锁保护；`WidgetConfigureActivity` 将保存配置事务与桌面宿主刷新解耦。

### 应用内更新模块加固
- **残留分片清理与竞态防护（CR-04 / CR-06）**：`UpdateCenter` 初始化及每次下载前自动清理非活跃 `.part` 残留文件；`check()` 持有并取消旧检查任务，防止并发请求覆盖最新检查结果。
- **完整性错误语义保真（CR-05）**：`UpdateException` 新增 `isIntegrityError` 标志，SHA256 校验失败不再被备用源的网络异常文案掩盖。

## 验证

- **JVM 单元测试**：`72/72` 通过（`:app:testDebugUnitTest`，新增 5 项回归测试）。
- **Android 仪器测试**：`52/52` 通过（MuMu `LGE-AN10`，Android 15 / API 35：`RepositoryTest` 23、`MigrationTest` 1、`WidgetRenderingTest` 9、`FrameworkUiTest` 16、`WidgetPinTest` 3）。
- **Android Lint**：`0 error`（`:app:lintDebug`）。
- **原型与手账检查**：`prototype/warm/verify.cjs` 30/30 通过，`npm test` 30/30 通过。

## 构建

- `versionCode` 12，`versionName` `0.4.1`
- 安装包：`releases/wanglemai-0.4.1-debug.apk`（SHA256：`a9888c3ed353551273ce4be91dee9f070c9e7d54d76fb079318fcb5413024e86`，沿用既有 debug keystore 签名，支持覆盖安装升级）

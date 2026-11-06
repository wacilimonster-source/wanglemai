# 验收清单 v1.0

标记规则：未执行 / 通过 / 失败 / 不适用。必须填设备/系统/构建号和证据，不能用浏览器原型测试替代 Android 真机测试。

| ID | Given / When | Then |
|---|---|---|
| AC01 | 首次离线启动 | 幂等生成两默认清单，无样例用户记录，无登录或权限阻挡 |
| AC02 | Widget 绑定日常采购，点新增 | 直接进入编辑并选中日常采购，标题可输入，键盘不挡保存 |
| AC03 | 输入牛奶/2/盒，保存 | 成功后返回原桌面；App 与所有绑定该清单 Widget 同步 |
| AC04 | 标题为空、201码点、Emoji边界 | 正确校验，不按 UTF16错误截断；失败保留全部输入 |
| AC05 | 批量含空行、CRLF、51条或非法某行 | 显示条数；非法指出原行号；失败零新增；合法一次全保存 |
| AC06 | 重复点击/重试同一个 commandId | 只产生一份记录/批次 |
| AC07 | Widget 圆圈点击两次、旧版本动作重放 | 状态不反转，不启动编辑器，不污染另一条记录 |
| AC08 | Widget 完成→撤销 | 恢复原状态及排序；其他实例同步；页脚撤销清除 |
| AC09 | 完成后在 App 编辑记录，再点旧撤销 | 不覆盖新内容，提示冲突/失效并移除入口 |
| AC10 | App 编辑与另一路更新竞争 | expectedVersion 冲突被识别，编辑文本保留，用户可重新加载 |
| AC11 | 在待办/待买切换、改清单、搜含%/_文字 | 类型记忆独立，清单切换清搜索；按字面匹配标题备注 |
| AC12 | 置顶、新增、完成、恢复 | 遵守固定排序；已完成项不占 Widget 正常行数 |
| AC13 | Back/取消编辑，再从同入口进入 | 草稿恢复；成功保存后清除；编辑版本变化不静默覆盖 |
| AC14 | 删除普通清单，内有完成/未完成项和绑定 Widget | 全部迁移到同类默认清单，实例重绑，数据库无悬空引用 |
| AC15 | 尝试删除默认清单、同类型重名 | 不允许，错误说明清晰 |
| AC16 | 创建两个不同绑定实例并编辑其中一个配置 | 配置相互独立；PendingIntent 不串 ID |
| AC17 | 初次添加配置页取消/保存 | 取消不产生可用实例；保存有正确首帧 |
| AC18 | 小部件缩放、长标题、200%字号、深浅主题切换 | 不遮挡新增/保存；至少1行；全文编辑可看；TalkBack可用 |
| AC19 | App进程被普通终止/回收后点击Widget | 可重新读取数据和执行动作；不依赖内存缓存 |
| AC20 | 手机重启并解锁、桌面重启、APK原位升级 | 数据与配置恢复，实例可操作，无重复默认清单 |
| AC21 | 系统设置“强行停止”App | 如系统抑制广播，明确记录平台行为；用户手动重开后恢复，不承诺强停后仍可后台工作 |
| AC22 | 导出→新安装导入→再次导入 | 记录完整，第二次按ID跳过，Widget ID与草稿不迁入 |
| AC23 | 非法JSON/未知版本/5MB以上/悬空清单/恶意数量 | 预览前拒绝，不部分写库，不覆盖本机记录 |
| AC24 | 同ID异类型、同名异ID导入 | 异类型整批拒绝；同名加后缀并保持20码点上限 |
| AC25 | 模拟数据库写入失败/空间不足 | 不关闭编辑、不显示已保存、不丢原内容；重试可用 |
| AC26 | 完全断网并未允许后台自启动 | 核心新增/查看/完成仍可用；定时刷新不作为核心依赖 |
| AC27 | TalkBack、键盘导航、触控 | 圆圈正文独立播报，触控目标≥48dp；错误可感知 |

## 0.2.0 实测记录（2026-11-06）

环境：`emulator-5558`，Android 14/API34，sdk_gphone64_x86_64，1080x1920（420dpi）。未使用小米真机。自动化结果：JVM 33/33、设备 29/29；证据见 [开发记录](development-log.md) 与 [设备测试](evidence/android-instrumentation-0.2.0.txt)。

| ID | 结果 | 证据 / 说明 |
|---|---|---|
| AC01 | 通过 | `pm clear` 后启动，只有两个默认清单、无样例记录、无登录/权限阻挡 |
| AC02 | 通过 | 真实 AppWidgetHost 点新增直接进入 QuickEntryActivity，标题可输入、键盘不挡保存 |
| AC03 | 通过 | 小部件保存后返回宿主，App 与两个绑定实例同步；`realWidgetHostUpdatesTwoInstancesAndRunsPendingIntents` |
| AC04 | 通过 | 空标题/换行/201 码点由 ValidationTest；UI `validationErrorKeepsInput` 验证错误可见且输入保留 |
| AC05 | 通过 | ValidationTest 行号/50 条边界；RepositoryTest 原子与失败零新增；UI `batchEntryAndDraftRestore` |
| AC06 | 通过 | RepositoryTest 记录/批量重复 commandId 只写一次 |
| AC07 | 通过 | RepositoryTest 重复完成不反转、旧版本动作不污染其他记录 |
| AC08 | 通过 | 宿主测试完成→页脚撤销；两实例同步；撤销后入口消失 |
| AC09 | 通过 | 编辑清除 widget_undo，旧 token 返回 Conflict；未点击陈旧桌面页脚（入口已被主动刷新移除） |
| AC10 | 通过 | staleUpdate 冲突；UI `staleDraftSaveShowsConflictAndCanReloadLatest` 保留草稿并可载入最新 |
| AC11 | 通过 | Room 字面搜索；UI `settingsThemeAndLiteralSearch` 验证 `%_` 与切换清单清空搜索 |
| AC12 | 通过 | `pinnedAndRestoredItemsKeepFixedOrder`：置顶、sortKey、恢复位置、完成倒序、新增末尾 |
| AC13 | 通过 | `batchEntryAndDraftRestore`（取消/恢复/丢弃）、`backHidesImeBeforeKeepingDraft`、冲突测试 |
| AC14 | 通过 | RepositoryTest 删除清单迁移未完成/已完成/软删除项、重绑 widget、清撤销、迁移草稿 |
| AC15 | 通过 | 默认清单不可删；同类忽略大小写重名被拒绝；默认可改名 |
| AC16 | 通过 | 两个独立实例不同配置；PendingIntent data 带 widgetId/itemId/version；一个完成只在来源实例出现撤销 |
| AC17 | 通过 | `initialWidgetConfigureCancelLeavesNoConfig` 取消后无 config；保存配置后先 refresh 再 RESULT_OK |
| AC18 | 部分通过 | 标准/大号、字体 200%、深浅色、宽度/行数通过；长标题点击看全文由实现保证，未专门 UI 断言；TalkBack 未人工验收 |
| AC19 | 部分通过 | App force-stop 后冷启动数据可读；真实系统 Launcher 的小部件进程回收未验证 |
| AC20 | 部分通过 | 模拟器重启后 App 数据可读；多次 `install -r` 保留数据；真实 Launcher 小部件重启/升级恢复未验证 |
| AC21 | 部分通过 | 强停后用户手动重开数据正常；系统是否抑制 Widget 广播未在真机验证 |
| AC22 | 通过 | SAF 导出→`pm clear`→导入恢复 3 条；RepositoryTest 重复导入按 ID 跳过；第二次手机 UI 导入未完整重复 |
| AC23 | 通过 | BackupCodecTest 拒绝未知版本/app/超大/非法字段/悬空引用；RepositoryTest 冲突/悬空零写入 |
| AC24 | 通过 | 同 ID 异类型整批拒绝；同名新清单加“（导入2）”且 20 码点上限 |
| AC25 | 未执行 | 未做存储写满/IO 故障注入；编辑失败保留输入由 validation UI 测试覆盖，真实 StorageError 未测 |
| AC26 | 通过 | INTERNET 仅用于手动检查 GitHub Releases；核心新增/查看/完成均为本地 Room，断网不影响，不依赖定时刷新 |
| AC27 | 部分通过 | 勾选/正文独立语义、48dp 目标、live region、内容描述已实现；TalkBack 人工验收未执行 |

补充：SAF 导出文件 1678 bytes；导入预览显示 2 个清单、3 条记录，新增 0 个清单、3 条记录，跳过 2 项。系统 light/dark 与 200% 字体截图见 [开发记录](development-log.md)。

## 性能目标（真机目标，不是已测结果）

同一目标机，release-like 构建，各测20次并记录中位数/P95：Widget 点击到可输入 P95≤1秒；本地保存到桌面可见 P95≤1秒。10,000条记录下列表进入/搜索无明显主线程阻塞；慢存储时先保证不丢数据。达不到时记录原因和数据，不改测试口径掩盖。

## 设备矩阵

| 设备 | 系统 / Android / 桌面版本 | 重点 | 状态 |
|---|---|---|---|
| 用户实际小米手机（型号待提供） | 已确认澎湃 OS 3；Android / 桌面版本待填 | 输入、桌面、字体、深色、进程恢复；负一屏另验 | 未执行 |
| Android API26 模拟器 | 待搭建 | 最低版本安装、资源与 PendingIntent 兼容 | 未执行（本机无 API26 镜像） |
| API31+ 模拟器/设备 | 待搭建 | 尺寸重配、深浅色、导入导出 | 未执行 |
| API36 模拟器/设备 | 待搭建 | target 行为与系统限制 | 未执行 |
| emulator-5558（pika_test） | Android14/API34，1080x1920 | 构建、Room、UI、AppWidgetHost、SAF、重启、字体、深浅色 | 通过（不等同小米真机） |

## 发布门槛

AC01～AC27 无阻断数据丢失/串条目/重复新增问题；核心 AC02/03/07/08/16/19 在用户小米真机通过。构建、测试、未通过项如实记录。没有真机证据只能交付“待真机验收 debug 版本”，不能宣布小米适配完成。

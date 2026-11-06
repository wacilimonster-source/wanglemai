> 状态说明（2026-11-06）：v0.2.2 MVP 已实现，本文件保留为最初启动指令。继续开发前以 [框架状态](07-framework-status.md)、[开发记录](development-log.md) 和 [验收记录](03-acceptance.md) 为准；不要重复已完成的 MVP 功能。下一阶段是 T6：小米真机普通桌面、API 矩阵、TalkBack、性能与真实 Launcher 生命周期验证；负一屏已因小米小部件要求应用商店上架而评估为不开发；更新检查已支持 GitHub 主源与 jsDelivr 国内回退。

# 可直接交给其他模型的开发启动指令

你接手一个已有原生基础框架的 Android 项目“忘了没”，工作目录为 `G:\game\nw\wanglemai`。用户目标系统已确定为澎湃 OS 3。请从现有 app/ 工程继续完成 MVP，不要重新生成工程覆盖已有代码，不要只输出计划，也不要把浏览器原型当成正式应用。

先读 README.md、docs/07-framework-status.md、docs/development-log.md，再读 docs/01-product.md、docs/02-architecture.md、docs/03-acceptance.md、docs/04-agent-tasks.md，并查看 prototype/index.html。07 文档界定真实已实现范围，开发记录列实际测试结果；产品/架构/验收定义完整目标。优先补缺失能力，不重复做已通过的工程初始化。

使用 Kotlin + Jetpack Compose/Material3 + Room + Coroutines/StateFlow。桌面 Widget 用 AppWidgetProvider + XML RemoteViews。applicationId 暂定 com.wanglemai.app，minSdk26、compile/targetSdk36；搭建时核验并固定相容的稳定依赖，提供 Gradle Wrapper。MVP 使用标准 Android Widget，在应用默认进程实现，不依赖小米小部件中心审核，不接入专有独立进程。无登录、云同步、提醒、AI、网络后台服务或悬浮窗，不申请无关权限。

必须实现：两种类型清单及不可删除的默认清单、记录新增编辑软删除、置顶和固定排序、待买数量单位、最多50条的原子批量录入、字面搜索、完成恢复与版本安全撤销、草稿恢复、新建重命名删除清单并迁移记录、两种尺寸Widget、多实例独立绑定和配置、浅深色主题、SAF JSON备份及合并恢复。

Android Widget 不支持普通 EditText。新增和正文点击必须通过 PendingIntent.getActivity 直接进入 QuickEntryActivity；自动聚焦输入，保存成功后返回原桌面，勾选在Widget内执行。不要用Receiver/Service间接启动编辑页。所有界面共用Room业务数据；写入后显式刷新受影响的全部Widget，不能依赖定时轮询。使用expectedVersion条件更新、防重commandId、事务批量、错误保留输入，遵守文档的撤销和备份协议。

按T0～T6依赖继续工作；先复现现有构建和关键测试，保留 gradle/libs.versions.toml 已固定的相容版本。下一阶段重点是自定义清单 CRUD/迁移、批量录入、长期草稿、SAF备份恢复、实例主题密度和体验完善。可独立完成的常规选择直接决定并记录。没有明确授权时不要自动启动其他Agent；若由用户安排多人协作，按任务归属和接口契约交接。

运行适当的单元、Room和界面测试；在明确选定的模拟器/真机上验证多实例、进程回收、系统重启、缩放、深色、输入法和备份恢复，不要默认向全部已连接设备安装。已知系统为 HyperOS 3，机型、Android版本与智能助理版本尚未提供。必须先验证普通桌面，再单独验证负一屏是否接纳本地标准Widget；不能把模拟器 AppWidgetHost 测试通过视为小米负一屏通过。若需要专有适配/审核，单独记录，不擅自Root或更改系统。环境缺失时继续独立工作，如实标注未执行验证。

最终交付原生工程、构建和测试命令及实际结果、debug APK（构建条件具备时）、安装与添加Widget步骤、验收记录、已知限制；更新docs/development-log.md。不要擅自上架、联网发布或增加产品范围。

# 开发任务与交接 v1.0

本文件可供一个 Agent 串行执行，也可由用户安排多个 Agent 分工。当前没有启动或分派开发 Agent。

实施更新：当前已交付原生 v0.2.2 MVP。T0-T5 主体已实现并在 Android14/API34 模拟器通过 38 项 JVM + 29 项设备测试；T6 的小米真机普通桌面、API26/31/36、TalkBack 人工验收、性能与真实 Launcher 生命周期仍未完成；负一屏因小米小部件准入要求应用商店上架，在本项目“不上架、自有 APK”约束下已评估为不开发。先读 [现状](07-framework-status.md) 和 [实测记录](development-log.md)，继续未验收项，不要重复已完成功能或覆盖现有工程。

## 依赖顺序

`T0 → T1 → T2 → (T3 / T4) → T5 → T6`。T3/T4 可在 T2 接口稳定后并行；共享模型和版本目录由集成负责人维护。

| 任务 | 工作边界 | 依赖 | 完成定义 |
|---|---|---|---|
| T0 工程与风险验证 | 创建 Android 工程、锁版本、Gradle Wrapper、搭建最小 Widget→QuickEntry→保存→刷新链 | 无 | assembleDebug 成功；安装至少一个模拟器；记录小米真机待验项 |
| T1 领域契约 | 数据类/枚举/校验器/排序/MutationResult/业务接口、两默认ID | T0 | 单元通过；与本文档字段一一对应；不依赖 UI |
| T2 数据实现 | Room/schema/DAO/CAS/幂等command/草稿/清单迁移/备份事务 | T1 | Room instrumented 核心事务用例通过；导出schema；无破坏性迁移 |
| T3 App与编辑 | Compose主导航/清单/搜索/完成/编辑/草稿/数量/批量/失败恢复/设置 | T2 | 原型主流程对应原生界面；AC04/05/10/11/13/15通过 |
| T4 Widget | XML标准/大号、配置、动作Receiver、Coordinator、状态页脚、恢复与尺寸 | T2 | AC02/03/07/08/16/17通过；无Activity trampoline；多实例不串参 |
| T5 导入导出与集成 | SAF/预览合并/冲突、端到端一致性、可访问性 | T3,T4 | AC14/18/22/23/24/25通过；5000+记录手工检查 |
| T6 小米验收与交付 | 设备矩阵、进程/重启/升级、性能、debug APK、操作说明 | T5 | 提交实测结果；明确未执行项，不伪造适配结论 |

## 建议文件归属

- 契约/集成：domain/**、AppContainer、Gradle、Manifest、README、文档。
- 数据：data/**、Room schema、对应测试。
- App：ui/**；不直接访问 DAO。
- Widget：widget/**、res/layout/widget*、res/xml/widget*；不直接复制业务校验。

合作时先冻结 domain 类型再并行，Manifest 和版本目录改动交集成负责人统一处理。禁止在多个分支各自发明 Backup JSON 或字段名称。

## 每次交接必须包含

变更文件、已完成验收ID、实际运行命令及结果、未执行的设备项、接口变更、已知缺陷、下一步。记录到 docs/development-log.md（开发时新建）。暂时性失败不要改需求掩盖；SDK/真机缺失直接记录。

## 实施约束

不改 HTML 就声称 Android 完成；不增加云服务或账号；不为小米适配加入常驻前台服务；不申请无关权限；不将真实用户记录写入日志或测试快照；无用户指令不执行市场上架或发布。

# 原型检查记录

日期：2026-11-06。检查对象：本交接包的浏览器原型，不是 Android App。

## 实际执行

- `node --check prototype/app.js`：通过。
- `npm test`：30 项检查通过，浏览器 pageerror 为 0。Playwright 1.65.0 + 本机 Microsoft Edge，headless，通过 file URL 打开原型。
- 补充样式检查：系统深色时，显式 LIGHT 的 Widget 文字 `rgb(32,45,40)`、背景 `rgb(255,255,255)`，通过。
- 已人工查看桌面、编辑器、App、360px 深色全页截图：主体文字和操作未发现重叠；大号卡片下方内容在手机预览区域可滚动查看。
- 完整脚本在 tests/prototype.test.cjs；30 项结果在 prototype-checks.json。

## 覆盖流程

两实例初始状态、继承清单、空标题/非法数量拒绝、新增持久化、桌面完成撤销、同清单两实例修改同步、批量空行处理和非法行原子拒绝、草稿恢复及丢弃、App 搜索和撤销、字面通配符搜索、新建及删除清单迁移、默认清单保护、导出格式、重复导入按ID跳过、未知版本拒绝、新记录合并、浏览器刷新保留数据、深色切换、大号布局、360px无水平溢出。

## 截图

- [桌面](prototype-desktop.png)
- [快速编辑](prototype-editor.png)
- [配套 App](prototype-app.png)
- [360px 深色](prototype-mobile-dark.png)

## 复现

仅查看原型不需要安装任何依赖，用浏览器打开 prototype/index.html 即可。复现自动化检查时，在项目根目录运行 `npm ci --ignore-scripts` 后 `npm test`；测试默认使用已安装的 Microsoft Edge（channel=msedge），未安装 Edge 的机器需调整为可用的 Playwright 浏览器。

## 尚未验证

本文件保留的是浏览器原型阶段的检查结果；后续已新增原生框架，原生构建与模拟器结果请查 [开发记录](development-log.md)。网页显示同步和浏览器刷新通过，不能推导为 Android 验收通过。小米真机/负一屏、重启升级、强行停止、SAF 和完整 TalkBack 等仍需对应设备证据。

本记录不等于 docs/03-acceptance.md 的 AC 用例已在 Android 上通过。开发者应新建原生验收记录，逐项填写实际设备和结果。

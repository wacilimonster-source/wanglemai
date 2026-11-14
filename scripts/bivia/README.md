# Bivia 决策辅助测试工具

只做**建议**，不做测试裁决，也不作为 `docs/evidence/` 的证据。所有结论都必须由已经执行的 JVM、instrumentation 或真机结果确认。

## 安装

```bash
python -m venv .venv-bivia
source .venv-bivia/Scripts/activate        # Git Bash
pip install -r scripts/bivia/requirements.txt
export A8_API_KEY="..."
```

`.venv-bivia/` 已加入 `.gitignore`。密钥只走环境变量，不写进任何文件。

## 冒烟

```bash
python scripts/bivia/decide.py scripts/bivia/cases/smoke/ticket-owner.json \
  --out test-results/bivia/smoke
```

预期输出 `status: answered`、一个选项和 `distribution`。

## 失败分诊

```bash
python scripts/bivia/triage_failures.py \
  --input scripts/bivia/cases/failures/known.json \
  --out test-results/bivia/failures
```

输出：

- `decisions.json`：原始判断、置信度、分布，便于追溯
- `summary.md`：给人看的建议表和人工标注准确率

规则：

- `status == "abstained"` 一律标“人工”
- 置信度 ≥ 0.8 才标“建议”，否则标“仅供参考”
- 单次调用失败只记录错误，不中断其他样本，也不影响测试退出码

## 边界

- 不发送 `local.properties`、签名文件、用户数据；`state` 只放测试名、失败摘要和最小代码片段。
- 不自动修改测试或业务代码。
- 输出目录 `test-results/` 已被 git 忽略，不进入发布证据。
- 当前只有 2 条标注样本，准确率数字只用于验证管线，不能当作模型质量结论。

## 研究结论（2026-11-09）

- **适合**：原子代码事实（3/3）、事实性 boolean 门控（3/3）、验收证据分级（6/7）。
- **不适合**：缺陷声明最终裁决（0/3，且高置信度错误）、失败根因五分类（flaky 误判）、测试范围 `multi_select`（1/3，过度选择）。
- **正确姿势**：先把复合问题拆成 3～5 个原子问题，让 Bivia 只回答事实；再用确定性规则汇总。不要问“这条声明成立吗”。
- **置信度**：只在同一任务类型内有参考价值，不能跨任务当自动采纳门槛。
- **SDK 缺陷**：`multi_select` 空集返回 `None`，SDK 会抛 `ValidationError`；`run_cases.py` 已按 `[]` 处理并记录 `sdk_error`。

详见 [决策模型辅助测试研究](../../docs/decision-model-testing-research.md)。

## 下一步

1. 实现 `verify_claim.py`：声明 → 原子事实 → 规则汇总，先在 10 条历史声明上评测。
2. 把 AC01～AC27 全部做证据分级，与人工结论对照。
3. 失败分诊改成事实性 boolean + 确定性行动表，用 20 条历史失败验证。
4. 测试范围改成路径规则表；Bivia 只回答原子影响面问题。
5. 每个任务至少 30 条标注，未达标不接入发版流程。

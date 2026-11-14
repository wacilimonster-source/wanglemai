#!/usr/bin/env python3
"""Triage test failures with Bivia. Advisory only, never a pass/fail gate."""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

from decide import ADVISORY, record_for, run_case

CHOICES = [
    "产品缺陷",
    "测试等待/时序不稳定",
    "测试选择器或测试数据错误",
    "环境/设备问题",
    "无法判断",
]

ACTIONS = {
    "产品缺陷": "保留失败，写复现步骤，人工确认后提 issue",
    "测试等待/时序不稳定": "加显式等待；允许一次有记录的重跑，标记 flaky",
    "测试选择器或测试数据错误": "修测试数据或选择器",
    "环境/设备问题": "换设备或重装 APK/测试 APK 后重跑",
    "无法判断": "人工看日志",
}

QUESTION = "这次失败最可能属于哪一类？"

RUBRIC = """判定口径：
- 测试等待/时序不稳定：同一代码、同一数据、同一设备重跑可通过；失败与异步加载、动画、首帧或等待不足有关。
- 测试选择器或测试数据错误：选择器与当前 UI 不一致，或测试数据不满足前置条件；重跑仍会稳定失败。
- 环境/设备问题：测试框架、UTP、设备连接、安装或系统状态导致，非产品逻辑也非用例逻辑。
- 产品缺陷：可稳定复现的产品行为错误。
- 无法判断：现有信息不足以区分以上类别。"""


def load_failures(path: Path) -> list[dict[str, Any]]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"cannot read failures {path}: {exc}") from exc
    failures = data.get("failures") if isinstance(data, dict) else data
    if not isinstance(failures, list) or not failures:
        raise SystemExit(f"expected a non-empty failure list: {path}")
    return failures


def build_case(failure: dict[str, Any], index: int) -> dict[str, Any]:
    state_lines = [
        f"测试: {failure.get('test', 'unknown')}",
        f"结果: {failure.get('result', 'unknown')}",
        f"断言/异常: {failure.get('assertion', '未提供')}",
        f"设备: {failure.get('device', '未提供')}",
        f"日志摘要: {failure.get('log_excerpt', '未提供')}",
        f"历史: {failure.get('history', '未提供')}",
        "",
        RUBRIC,
    ]
    return {
        "id": failure.get("id") or f"failure-{index}",
        "type": "choice",
        "question": QUESTION,
        "choices": CHOICES,
        "state": "\n".join(state_lines),
        "domain": "software testing, Android instrumentation",
        "expected": failure.get("expected"),
    }


def strength(record: dict[str, Any], threshold: float) -> str:
    if record.get("status") == "abstained":
        return "人工"
    confidence = record.get("confidence")
    if isinstance(confidence, (int, float)) and confidence >= threshold:
        return "建议"
    return "仅供参考"


def render_markdown(records: list[dict[str, Any]], threshold: float) -> str:
    lines = [
        "# Bivia 失败分诊（仅供参考）",
        "",
        f"> {ADVISORY}",
        "",
        f"置信度门槛：{threshold}（达到门槛才标“建议”，否则“仅供参考”）。",
        "",
        "| 用例 | 模型判断 | 置信度 | 强度 | 建议行动 | 人工标注 |",
        "|---|---|---|---|---|---|",
    ]
    for record in records:
        if "error" in record:
            lines.append(
                f"| {record['case']} | 调用失败 | - | 人工 | {record['error']} | - |"
            )
            continue
        value = record.get("value")
        confidence = record.get("confidence")
        confidence_text = f"{confidence:.2f}" if isinstance(confidence, (int, float)) else "-"
        expected = record.get("expected") or "-"
        lines.append(
            f"| {record['case']} | {value} | {confidence_text} | "
            f"{strength(record, threshold)} | {ACTIONS.get(value, '人工看日志')} | {expected} |"
        )

    labeled = [r for r in records if "error" not in r and r.get("expected")]
    if labeled:
        correct = sum(1 for r in labeled if r.get("value") == r["expected"])
        lines += ["", f"人工标注样本准确率：{correct}/{len(labeled)}。样本少，仅用于冒烟，不构成质量结论。"]
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="JSON failure list")
    parser.add_argument("--out", type=Path, required=True, help="output directory")
    parser.add_argument("--mode", choices=["fast", "deep"], default="fast")
    parser.add_argument("--threshold", type=float, default=0.8)
    parser.add_argument("--dry-run", action="store_true", help="print cases without calling the API")
    args = parser.parse_args(argv)

    failures = load_failures(args.input)
    cases = [build_case(failure, index) for index, failure in enumerate(failures)]

    if args.dry_run:
        print(json.dumps(cases, ensure_ascii=False, indent=2))
        return 0

    if not os.environ.get("A8_API_KEY"):
        raise SystemExit("A8_API_KEY is not set")

    records: list[dict[str, Any]] = []
    for case in cases:
        try:
            result = run_case(case, args.mode, allow_abstain=True)
            record = record_for(case, Path(case["id"]), "choice", args.mode, result)
            record["expected"] = case.get("expected")
        except Exception as exc:  # noqa: BLE001 - one failed call must not stop the batch
            record = {"case": case["id"], "error": f"{type(exc).__name__}: {exc}"}
        records.append(record)
        if "error" in record:
            print(f"[error] {record['case']}: {record['error']}")
        else:
            reason = record.get("abstain_reason")
            suffix = f" ({reason})" if reason else ""
            print(f"[{record['status']}] {record['case']}: {record['value']}{suffix}")

    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "decisions.json").write_text(
        json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    markdown = render_markdown(records, args.threshold)
    (args.out / "summary.md").write_text(markdown, encoding="utf-8")
    print(markdown)
    print(f"wrote: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

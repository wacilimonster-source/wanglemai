#!/usr/bin/env python3
"""Run a batch of Bivia cases and compare answers with human labels."""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

from decide import ADVISORY, record_for, run_case


def load_cases(path: Path) -> list[dict[str, Any]]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"cannot read cases {path}: {exc}") from exc
    cases = data.get("cases") if isinstance(data, dict) else data
    if not isinstance(cases, list) or not cases:
        raise SystemExit(f"expected a non-empty case list: {path}")
    return cases


def display(value: Any) -> str:
    if isinstance(value, (list, dict)):
        return json.dumps(value, ensure_ascii=False)
    return "-" if value is None else str(value)


def confidence_text(confidence: Any) -> str:
    if isinstance(confidence, dict) and confidence:
        values = [v for v in confidence.values() if isinstance(v, (int, float))]
        if values:
            return f"min={min(values):.2f}"
    if isinstance(confidence, (int, float)):
        return f"{confidence:.2f}"
    return "-"


def matches(record: dict[str, Any]) -> bool | None:
    expected = record.get("expected")
    if expected is None:
        return None
    value = record.get("value")
    if isinstance(expected, list):
        return isinstance(value, list) and set(value) == set(expected)
    return value == expected


def render(records: list[dict[str, Any]]) -> str:
    lines = [
        "# Bivia 决策辅助评测（仅供参考）",
        "",
        f"> {ADVISORY}",
        "",
        "| 用例 | 类型 | 模型结果 | 置信度 | 状态 | 人工标注 | 匹配 |",
        "|---|---|---|---|---|---|---|",
    ]
    for record in records:
        if "error" in record:
            lines.append(
                f"| {record['case']} | - | 调用失败 | - | error | - | - |"
            )
            continue
        expected = record.get("expected")
        expected_text = display(expected) if expected is not None else "-"
        result = "弃权" if record.get("status") == "abstained" else display(record.get("value"))
        matched = matches(record)
        matched_text = "-" if matched is None else ("是" if matched else "否")
        lines.append(
            f"| {record['case']} | {record.get('type')} | {result} | "
            f"{confidence_text(record.get('confidence'))} | {record.get('status')} | "
            f"{expected_text} | {matched_text} |"
        )

    by_type: dict[str, list[dict[str, Any]]] = {}
    for record in records:
        if "error" in record or record.get("expected") is None:
            continue
        by_type.setdefault(record.get("type", "?"), []).append(record)
    if by_type:
        lines += ["", "## 按类型汇总", ""]
        for kind, items in sorted(by_type.items()):
            correct = sum(1 for item in items if matches(item))
            abstained = sum(1 for item in items if item.get("status") == "abstained")
            lines.append(
                f"- {kind}: {correct}/{len(items)} 匹配，{abstained} 条弃权"
            )
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="JSON case list")
    parser.add_argument("--out", type=Path, required=True, help="output directory")
    parser.add_argument("--mode", choices=["fast", "deep"], default="fast")
    parser.add_argument("--dry-run", action="store_true", help="print cases without calling the API")
    args = parser.parse_args(argv)

    cases = load_cases(args.input)
    if args.dry_run:
        print(json.dumps(cases, ensure_ascii=False, indent=2))
        return 0
    if not os.environ.get("A8_API_KEY"):
        raise SystemExit("A8_API_KEY is not set")

    records: list[dict[str, Any]] = []
    for case in cases:
        try:
            result = run_case(case, args.mode, allow_abstain=True)
            record = record_for(case, Path(case.get("id", "case")), case.get("type", "choice"), args.mode, result)
            record["expected"] = case.get("expected")
            record["run_mode"] = args.mode
        except Exception as exc:  # noqa: BLE001 - one failed call must not stop the batch
            message = f"{type(exc).__name__}: {exc}"
            if (
                case.get("type") == "multi_select"
                and "SetDecision" in message
                and "input_value=None" in message
            ):
                record = {
                    "case": case.get("id", "case"),
                    "type": "multi_select",
                    "status": "answered",
                    "value": [],
                    "confidence": {},
                    "distribution": None,
                    "abstain_reason": None,
                    "expected": case.get("expected"),
                    "sdk_error": message,
                    "note": "Bivia selected no option; SDK rejected None. Treated as empty set.",
                }
            else:
                record = {"case": case.get("id", "case"), "error": message}
        records.append(record)
        if "error" in record:
            print(f"[error] {record['case']}: {record['error']}")
        else:
            result = "abstained" if record.get("status") == "abstained" else display(record.get("value"))
            print(f"[{record.get('status')}] {record['case']}: {result}")

    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "results.json").write_text(
        json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    markdown = render(records)
    (args.out / "summary.md").write_text(markdown, encoding="utf-8")
    print(markdown)
    print(f"wrote: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

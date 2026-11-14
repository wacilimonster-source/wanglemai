#!/usr/bin/env python3
"""Run one Bivia decision from a JSON case file.

Advisory only: the result is never test evidence. See README.md.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path
from typing import Any

ADVISORY = "Advisory only. Not test evidence; confirm with executed tests."


def load_case(path: Path) -> dict[str, Any]:
    try:
        case = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"cannot read case {path}: {exc}") from exc
    if not isinstance(case, dict):
        raise SystemExit(f"case must be a JSON object: {path}")
    for key in ("question", "state"):
        if not case.get(key):
            raise SystemExit(f"case missing required field: {key}")
    kind = case.get("type", "choice")
    if kind == "choice" and not case.get("choices"):
        raise SystemExit("choice case needs choices")
    if kind == "multi_select" and not case.get("options"):
        raise SystemExit("multi_select case needs options")
    return case


def run_case(case: dict[str, Any], mode: str, allow_abstain: bool):
    try:
        from bivia import Bivia
    except ImportError as exc:
        raise SystemExit(
            "bivia SDK not installed; run: pip install -r scripts/bivia/requirements.txt"
        ) from exc

    kind = case.get("type", "choice")
    kwargs = {
        "question": case["question"],
        "state": case["state"],
        "mode": mode,
        "allow_abstain": allow_abstain,
    }
    if case.get("domain"):
        kwargs["domain"] = case["domain"]
    with Bivia(timeout=30, max_retries=2) as client:
        if kind == "choice":
            return client.decisions.choice(choices=case["choices"], **kwargs)
        if kind == "multi_select":
            return client.decisions.multi_select(options=case["options"], **kwargs)
        if kind == "boolean":
            return client.decisions.boolean(**kwargs)
        raise SystemExit(f"unsupported case type: {kind}")


def confidence_for(kind: str, result):
    value = getattr(result, "value", None)
    if kind == "choice":
        distribution = getattr(result, "distribution", None) or {}
        return distribution.get(value)
    if kind == "boolean":
        return getattr(result, "probability", None)
    if kind == "multi_select":
        return getattr(result, "probabilities", None)
    return None


def record_for(case: dict[str, Any], case_path: Path, kind: str, mode: str, result) -> dict[str, Any]:
    distribution = getattr(result, "distribution", None)
    if distribution is None:
        distribution = getattr(result, "probabilities", None)
    return {
        "case": case.get("id", case_path.stem),
        "case_file": str(case_path),
        "advisory": ADVISORY,
        "type": kind,
        "mode": mode,
        "question": case["question"],
        "state_sha256": hashlib.sha256(case["state"].encode("utf-8")).hexdigest(),
        "request": {
            "state": case["state"],
            "choices": case.get("choices"),
            "options": case.get("options"),
        },
        "status": getattr(result, "status", None),
        "value": getattr(result, "value", None),
        "confidence": confidence_for(kind, result),
        "distribution": distribution,
        "abstain_reason": getattr(result, "abstain_reason", None),
    }


def write_record(record: dict[str, Any], out: Path | None) -> Path | None:
    if out is None:
        return None
    if out.suffix.lower() == ".json":
        target = out
    else:
        out.mkdir(parents=True, exist_ok=True)
        target = out / f"{record['case']}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return target


def print_summary(record: dict[str, Any]) -> None:
    if record["status"] == "abstained":
        print(f"[abstained] {record['case']}: {record.get('abstain_reason') or 'model declined'}")
        return
    print(f"[{record['status']}] {record['case']}: {record['value']}")
    if record["confidence"] is not None:
        print(f"  confidence: {record['confidence']}")
    print(f"  {ADVISORY}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("case", type=Path, help="JSON case file")
    parser.add_argument("--out", type=Path, help="output file or directory")
    parser.add_argument("--mode", choices=["fast", "deep"], default="fast")
    parser.add_argument("--no-abstain", action="store_true", help="force a best-effort answer")
    parser.add_argument("--dry-run", action="store_true", help="print the request without calling the API")
    args = parser.parse_args(argv)

    case = load_case(args.case)
    if args.dry_run:
        print(
            json.dumps(
                {
                    "question": case["question"],
                    "state": case["state"],
                    "choices": case.get("choices"),
                    "options": case.get("options"),
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        return 0

    if not os.environ.get("A8_API_KEY"):
        raise SystemExit("A8_API_KEY is not set")

    result = run_case(case, args.mode, not args.no_abstain)
    record = record_for(case, args.case, case.get("type", "choice"), args.mode, result)
    target = write_record(record, args.out)
    print_summary(record)
    if target:
        print(f"  wrote: {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

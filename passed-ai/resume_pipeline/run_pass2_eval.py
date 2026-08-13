"""Pass 2 골든셋 생성 및 preview 평가 CLI."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from .pass2_eval import (
    build_golden_from_manual_audit,
    evaluate_pass2_report,
    extend_golden_from_prediction_audit,
    load_pass2_golden,
)
from .skill_recall_worker import RecallExperimentReport


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Strict Pass 2 preview 평가")
    parser.add_argument("--golden", type=Path, required=True)
    parser.add_argument("--predictions", type=Path)
    parser.add_argument("--build-from-report", type=Path)
    parser.add_argument("--manual-audit", type=Path)
    parser.add_argument("--extend-from-predictions", type=Path)
    parser.add_argument("--base-golden", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)

    if args.extend_from_predictions:
        base_golden = args.base_golden or args.golden
        if not args.manual_audit or not base_golden.exists():
            parser.error(
                "--extend-from-predictions에는 기존 --base-golden과 "
                "--manual-audit가 필요합니다."
            )
        golden = extend_golden_from_prediction_audit(
            load_pass2_golden(base_golden),
            args.extend_from_predictions,
            args.manual_audit,
        )
        args.golden.write_text(
            json.dumps(
                [item.model_dump(mode="json") for item in golden],
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        return 0

    if args.build_from_report:
        if not args.manual_audit:
            parser.error("--build-from-report에는 --manual-audit가 필요합니다.")
        golden = build_golden_from_manual_audit(
            args.build_from_report, args.manual_audit
        )
        args.golden.parent.mkdir(parents=True, exist_ok=True)
        args.golden.write_text(
            json.dumps(
                [item.model_dump(mode="json") for item in golden],
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        return 0

    if not args.predictions:
        parser.error("평가에는 --predictions가 필요합니다.")
    golden = load_pass2_golden(args.golden)
    predictions = RecallExperimentReport.model_validate_json(
        args.predictions.read_text(encoding="utf-8")
    )
    result = evaluate_pass2_report(golden, predictions)
    rendered = result.model_dump_json(indent=2)
    print(rendered)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())

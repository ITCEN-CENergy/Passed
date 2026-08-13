"""두 개 이상의 저장된 예측 파일로 추출 안정성을 측정하는 CLI."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from .skill_extraction_eval import load_predictions
from .skill_extraction_models import SkillExtractionReport
from .skill_stability_eval import (
    evaluate_prediction_stability,
    extraction_report_to_predictions,
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="같은 골든셋을 반복 실행한 예측들의 Jaccard 안정성 평가"
    )
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument(
        "--predictions",
        type=Path,
        action="append",
        help="예측 JSON 경로. 3회 측정은 이 옵션을 세 번 지정합니다.",
    )
    source.add_argument(
        "--extractions",
        type=Path,
        action="append",
        help="실제 사용자 SkillExtractionReport JSON. 여러 번 지정할 수 있습니다.",
    )
    parser.add_argument("--output", type=Path, help="안정성 JSON 저장 경로")
    args = parser.parse_args(argv)
    paths = args.predictions or args.extractions
    if len(paths) < 2:
        parser.error("같은 종류의 입력 파일을 최소 두 번 지정해야 합니다.")

    runs = (
        [load_predictions(path) for path in paths]
        if args.predictions
        else [
            extraction_report_to_predictions(
                SkillExtractionReport.model_validate_json(
                    path.read_text(encoding="utf-8")
                )
            )
            for path in paths
        ]
    )
    report = evaluate_prediction_stability(runs)
    payload = report.model_dump_json(indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
    else:
        print(payload, end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())

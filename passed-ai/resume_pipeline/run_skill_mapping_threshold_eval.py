"""저장된 raw 매핑 결과에 임계값을 적용해 비용 없이 재평가하는 CLI."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from .skill_mapping_eval import evaluate_mapping_report
from .skill_mapping_models import load_mapping_golden_set
from .skill_mapping_worker import RawMappingReport


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="raw 매핑 결과 임계값 평가")
    parser.add_argument("--golden", type=Path, required=True)
    parser.add_argument("--raw", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--min-similarity", type=float, required=True)
    parser.add_argument("--min-margin", type=float, required=True)
    args = parser.parse_args(argv)

    if not 0.0 <= args.min_similarity <= 1.0:
        parser.error("--min-similarity는 0~1 범위여야 합니다.")
    if not 0.0 <= args.min_margin <= 1.0:
        parser.error("--min-margin은 0~1 범위여야 합니다.")

    cases = load_mapping_golden_set(args.golden)
    raw = RawMappingReport.model_validate_json(args.raw.read_text(encoding="utf-8"))
    report = evaluate_mapping_report(
        raw,
        cases,
        min_similarity=args.min_similarity,
        min_margin=args.min_margin,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report.model_dump_json(indent=2) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "output": str(args.output.resolve()),
                **report.model_dump(mode="json", exclude={"decisions"}),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

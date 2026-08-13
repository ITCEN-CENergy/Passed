"""mapping preview JSON에서 반복 unmapped 이름을 집계하는 CLI."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from .unmapped_skill_report import aggregate_unmapped_candidates
from .user_skill_mapping_models import UserSkillMappingReport


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="unmapped 스킬 후보 JSON 집계")
    parser.add_argument(
        "--mapping",
        type=Path,
        action="append",
        required=True,
        help="run_skill_mapping preview JSON. 여러 실행을 함께 집계할 수 있습니다.",
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    reports = [
        UserSkillMappingReport.model_validate_json(path.read_text(encoding="utf-8"))
        for path in args.mapping
    ]
    report = aggregate_unmapped_candidates(reports)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report.model_dump_json(indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())

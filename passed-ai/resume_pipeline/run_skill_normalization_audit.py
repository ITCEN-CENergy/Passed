"""매핑 골든셋으로 두 정규화 전략을 읽기 전용 비교하는 CLI."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from .db import connection
from .skill_mapping_models import load_mapping_golden_set
from .skill_normalization_audit import audit_normalization


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="스킬명 정규화 전략 감사")
    parser.add_argument("--golden", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)

    with connection() as conn:
        report = audit_normalization(conn, load_mapping_golden_set(args.golden))
    payload = report.model_dump_json(indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
    else:
        print(payload, end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())

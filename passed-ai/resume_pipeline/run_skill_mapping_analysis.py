"""매핑 골든셋의 EXACT·정규화 결과와 raw 임베딩 분포를 출력하는 CLI."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys

from .db import connection
from .skill_mapping_models import load_mapping_golden_set
from .skill_mapping_worker import (
    analyze_mapping_golden_set,
    load_skill_aliases,
    load_skill_masters,
    resolve_alias,
    resolve_exact_or_normalized,
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="임계값 적용 전 skills 마스터 raw 매핑 분포 분석"
    )
    parser.add_argument("--golden", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="EXACT·정규화 건수만 확인하고 임베딩 API를 호출하지 않음",
    )
    args = parser.parse_args(argv)

    cases = load_mapping_golden_set(args.golden)
    with connection() as conn:
        if args.dry_run:
            masters = load_skill_masters(conn)
            aliases = load_skill_aliases(conn)
            exact = normalized = alias_resolved = embedding_required = 0
            embedding_case_ids: list[str] = []
            for case in cases:
                method, _, _ = resolve_exact_or_normalized(case, masters)
                if method is None:
                    alias, _ = resolve_alias(case, aliases)
                    if alias is None:
                        embedding_required += 1
                        embedding_case_ids.append(case.case_id)
                    else:
                        alias_resolved += 1
                elif method.value == "EXACT":
                    exact += 1
                else:
                    normalized += 1
            print(
                json.dumps(
                    {
                        "total_cases": len(cases),
                        "exact_resolved": exact,
                        "normalized_resolved": normalized,
                        "alias_resolved": alias_resolved,
                        "embedding_required": embedding_required,
                        "embedding_case_ids": embedding_case_ids,
                        "openai_called": False,
                    },
                    ensure_ascii=False,
                    indent=2,
                )
            )
            return 0

        if not os.getenv("OPENAI_API_KEY"):
            parser.error("raw 임베딩 분석에는 OPENAI_API_KEY가 필요합니다.")
        report = analyze_mapping_golden_set(conn, cases, top_k=args.top_k)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report.model_dump_json(indent=2) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "output": str(args.output.resolve()),
                "total_cases": report.total_cases,
                "exact_resolved": report.exact_resolved,
                "normalized_resolved": report.normalized_resolved,
                "alias_resolved": report.alias_resolved,
                "embedding_analyzed": report.embedding_analyzed,
                "category_mismatch_cases": report.category_mismatch_cases,
                "missing_master_embeddings": report.missing_master_embeddings,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

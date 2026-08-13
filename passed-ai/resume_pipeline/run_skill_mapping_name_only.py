"""마스터 이름-only 임베딩 실험을 실행하고 JSON 보고서를 저장하는 CLI."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys

from .db import connection
from .embedding_worker import _create_embeddings, _create_openai_client
from .skill_mapping_models import load_mapping_golden_set
from .skill_mapping_name_only import analyze_name_only_embeddings
from .skill_mapping_worker import load_skill_masters


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="DB를 수정하지 않는 마스터 이름-only 임베딩 비교 실험"
    )
    parser.add_argument("--golden", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--batch-size", type=int, default=100)
    args = parser.parse_args(argv)

    if not os.getenv("OPENAI_API_KEY"):
        parser.error("이름-only 임베딩 실험에는 OPENAI_API_KEY가 필요합니다.")

    cases = load_mapping_golden_set(args.golden)
    with connection() as conn:
        masters = load_skill_masters(conn)

    client = _create_openai_client()
    report = analyze_name_only_embeddings(
        masters,
        cases,
        lambda texts: _create_embeddings(client, texts),
        top_k=args.top_k,
        batch_size=args.batch_size,
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report.model_dump_json(indent=2) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "output": str(args.output.resolve()),
                **report.summary.model_dump(mode="json"),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

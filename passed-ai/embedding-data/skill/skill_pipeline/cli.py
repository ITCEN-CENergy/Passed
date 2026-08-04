from __future__ import annotations

import argparse
import logging
import sys

import psycopg

from .config import get_settings
from .worker import run, similar_skills


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="skills 1536차원 임베딩 작업자")
    sub = parser.add_subparsers(dest="command", required=True)
    embed = sub.add_parser("embed", help="결측 임베딩 생성 및 DB 업데이트")
    embed.add_argument("--batch-size", type=int)
    embed.add_argument("--force", action="store_true", help="기존 벡터도 다시 생성")
    search = sub.add_parser("search", help="코사인 유사 스킬 검색")
    search.add_argument("query")
    search.add_argument("--category")
    search.add_argument("--limit", type=int, default=10)
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    settings = get_settings()
    if not settings.openai_api_key:
        parser.error("OPENAI_API_KEY가 필요합니다.")

    with psycopg.connect(settings.database_url) as conn:
        if args.command == "embed":
            stats = run(conn, force=args.force, batch_size=args.batch_size)
            print(
                f"selected={stats.selected} success={stats.success} failed={stats.failed} "
                f"skipped={stats.skipped} missing={stats.missing} "
                f"wrong_dimension={stats.wrong_dimension} prompt_tokens={stats.prompt_tokens}"
            )
            return 0 if not stats.failed and not stats.missing and not stats.wrong_dimension else 1

        if not 1 <= args.limit <= 100:
            parser.error("--limit는 1~100 범위여야 합니다.")
        for skill_id, name, category, similarity in similar_skills(
            conn, args.query, category=args.category, limit=args.limit
        ):
            print(f"{skill_id}\t{name}\t{category}\t{float(similarity):.4f}")
        return 0


if __name__ == "__main__":
    sys.exit(main())

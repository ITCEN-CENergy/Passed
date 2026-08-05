"""사용자 한 명의 이력서·자기소개서 청크 임베딩 CLI.

사용 예:
    python -m resume_pipeline.run_embedding --email test@passed.dev --dry-run
    python -m resume_pipeline.run_embedding --user-id 19 --batch-size 100
"""

from __future__ import annotations

import argparse
import logging
import os
import sys

from .db import EmbeddingSchemaError, connection, validate_embedding_schema
from .embedding_worker import (
    COVER_LETTER_USER_FILTER_SQL,
    EMBEDDING_BATCH_SIZE,
    EMBEDDING_DIM,
    EMBEDDING_MODEL,
    RESUME_USER_FILTER_SQL,
    count_pending_chunks,
    embed_pending_chunks,
)
from .user_resolver import UserNotFoundError, resolve_user_id


logger = logging.getLogger(__name__)


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="사용자의 PENDING 이력서·자기소개서 청크 임베딩"
    )
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--user-id", type=int, help="대상 사용자 ID")
    target.add_argument("--email", type=str, help="대상 사용자 이메일")
    parser.add_argument(
        "--batch-size",
        type=int,
        default=EMBEDDING_BATCH_SIZE,
        help=f"OpenAI 요청 한 번의 청크 수(기본 {EMBEDDING_BATCH_SIZE}, 최대 2048)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="대상 개수만 조회하고 API 호출과 DB 변경 없이 종료",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    if not 1 <= args.batch_size <= 2048:
        parser.error("--batch-size는 1~2048 범위여야 합니다.")

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )

    try:
        with connection() as conn:
            user_id = (
                args.user_id
                if args.user_id is not None
                else resolve_user_id(conn, args.email)
            )
            validate_embedding_schema(conn)

            resume_pending = count_pending_chunks(
                conn,
                "resume_chunks",
                filter_sql=RESUME_USER_FILTER_SQL,
                filter_params=(user_id,),
            )
            cover_pending = count_pending_chunks(
                conn,
                "cover_letter_chunks",
                filter_sql=COVER_LETTER_USER_FILTER_SQL,
                filter_params=(user_id,),
            )
            logger.info(
                "임베딩 대상 user_id=%s resume_chunks=%s cover_letter_chunks=%s "
                "model=%s dimension=%s",
                user_id,
                resume_pending,
                cover_pending,
                EMBEDDING_MODEL,
                EMBEDDING_DIM,
            )

            if args.dry_run:
                logger.info("dry-run 완료: OpenAI API와 DB UPDATE를 실행하지 않았습니다.")
                return 0

            if not os.getenv("OPENAI_API_KEY"):
                logger.error("OPENAI_API_KEY가 설정되지 않았습니다.")
                return 1

            resume_stats = embed_pending_chunks(
                conn,
                "resume_chunks",
                filter_sql=RESUME_USER_FILTER_SQL,
                filter_params=(user_id,),
                batch_size=args.batch_size,
            )
            cover_stats = embed_pending_chunks(
                conn,
                "cover_letter_chunks",
                filter_sql=COVER_LETTER_USER_FILTER_SQL,
                filter_params=(user_id,),
                batch_size=args.batch_size,
            )
    except (UserNotFoundError, EmbeddingSchemaError, ValueError) as exc:
        logger.error("임베딩 작업 중단: %s", exc)
        return 2

    logger.info(
        "임베딩 완료 user_id=%s resume_chunks=%s cover_letter_chunks=%s",
        user_id,
        resume_stats,
        cover_stats,
    )
    return 0 if resume_stats.failed + cover_stats.failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

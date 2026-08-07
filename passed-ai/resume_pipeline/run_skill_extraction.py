"""사용자 문서에서 매핑 전 스킬 후보를 추출하는 CLI.

사용 예:
    python -m resume_pipeline.run_skill_extraction --email test@passed.dev --dry-run
    python -m resume_pipeline.run_skill_extraction --email test@passed.dev --output candidates.json
"""

from __future__ import annotations

import argparse
import logging
import os
from pathlib import Path
import sys

from .db import connection
from .skill_extraction_worker import (
    SKILL_EXTRACTION_MODEL,
    extract_user_skill_candidates,
    load_extractable_chunks,
)
from .user_resolver import UserNotFoundError, resolve_user_id


logger = logging.getLogger(__name__)


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="COMPLETED 이력서·자기소개서 청크에서 스킬 후보 추출"
    )
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--user-id", type=int, help="대상 사용자 ID")
    target.add_argument("--email", type=str, help="대상 사용자 이메일")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="대상 청크만 조회하고 OpenAI API를 호출하지 않음",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="결과 JSON 파일 경로. 생략하면 표준 출력으로 내보냄",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
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
            chunks = load_extractable_chunks(conn, user_id)
            resume_count = sum(chunk.source_kind == "RESUME" for chunk in chunks)
            cover_count = sum(
                chunk.source_kind == "COVER_LETTER" for chunk in chunks
            )
            logger.info(
                "스킬 추출 대상 user_id=%s resume_chunks=%s "
                "cover_letter_chunks=%s model=%s",
                user_id,
                resume_count,
                cover_count,
                SKILL_EXTRACTION_MODEL,
            )

            if args.dry_run:
                logger.info("dry-run 완료: OpenAI API와 DB 변경을 실행하지 않았습니다.")
                return 0

            if not os.getenv("OPENAI_API_KEY"):
                logger.error("OPENAI_API_KEY가 설정되지 않았습니다.")
                return 1
            if not chunks:
                logger.error(
                    "추출할 COMPLETED 청크가 없습니다. 청킹과 임베딩을 먼저 실행하세요."
                )
                return 2

            report = extract_user_skill_candidates(conn, user_id)
    except UserNotFoundError as exc:
        logger.error("스킬 후보 추출 중단: %s", exc)
        return 2

    output_json = report.model_dump_json(indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output_json + "\n", encoding="utf-8")
        logger.info("스킬 후보 결과 저장 path=%s", args.output.resolve())
    else:
        print(output_json)

    logger.info(
        "스킬 후보 추출 종료 user_id=%s chunks=%s candidates=%s failures=%s",
        user_id,
        len(report.chunks),
        report.candidate_count,
        len(report.failures),
    )
    return 0 if not report.failures else 1


if __name__ == "__main__":
    sys.exit(main())

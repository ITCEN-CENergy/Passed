"""임베딩 작업자 실행 진입점(별도 Python 작업자).

기존 FastAPI 서버(app/main.py)와 분리되어 원본 적재와 독립적으로 재시작 가능.
사용: python -m job_posting_pipeline.run_embedding [--max-iterations N]
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

if __package__:
    from .config import get_settings
    from .db import DatabaseContractError, connection, validate_database_contract
    from .embedding_worker import run_embedding_worker
    from .logging_utils import configure_logging
else:
    # `python job_posting_pipeline/run_embedding.py` 직접 실행도 지원한다.
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    from job_posting_pipeline.config import get_settings
    from job_posting_pipeline.db import (
        DatabaseContractError,
        connection,
        validate_database_contract,
    )
    from job_posting_pipeline.embedding_worker import run_embedding_worker
    from job_posting_pipeline.logging_utils import configure_logging

logger = logging.getLogger(__name__)


# CLI는 적재 서버와 독립적으로 임베딩 작업자를 실행한다.
def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="미임베딩 청크 배치 임베딩 작업자")
    parser.add_argument(
        "--max-iterations", type=int, default=0,
        help="0이면 pending 이 없어질 때까지 반복(기본)",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        help="한 API 요청의 청크 수(기본: EMBEDDING_BATCH_SIZE, 최대 2048)",
    )
    parser.add_argument(
        "--log-file",
        help="로그 파일 경로(기본: embedding-data/job-posting/logs/embedding.log)",
    )
    args = parser.parse_args(argv)
    if args.batch_size is not None and not 1 <= args.batch_size <= 2048:
        parser.error("--batch-size는 1~2048 범위여야 합니다.")

    configure_logging("embedding", args.log_file)
    logger.info(
        "임베딩 작업 시작: max_iterations=%d batch_size=%s",
        args.max_iterations,
        args.batch_size or "env",
    )

    settings = get_settings()
    # 키가 없으면 DB를 조회하기 전에 종료해 불완전한 실행을 방지한다.
    if not settings.openai_api_key:
        logger.error("OPENAI_API_KEY 가 설정되지 않았습니다.")
        return 1

    try:
        with connection() as conn:
            validate_database_contract(conn)
            stats = run_embedding_worker(
                conn,
                max_iterations=args.max_iterations,
                batch_size=args.batch_size,
            )
    except DatabaseContractError as exc:
        logger.error("임베딩 작업 중단: %s", exc)
        return 1
    logger.info(
        "완료 processed=%d success=%d failed=%d skipped=%d "
        "prompt_tokens=%d remaining=%d",
        stats.processed,
        stats.success,
        stats.failed,
        stats.skipped,
        stats.prompt_tokens,
        stats.remaining,
    )
    return 0 if stats.failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

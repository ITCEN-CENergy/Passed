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
    from .db import connection
    from .embedding_worker import run_embedding_worker
    from .logging_utils import configure_logging
else:
    # `python job_posting_pipeline/run_embedding.py` 직접 실행도 지원한다.
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    from job_posting_pipeline.config import get_settings
    from job_posting_pipeline.db import connection
    from job_posting_pipeline.embedding_worker import run_embedding_worker
    from job_posting_pipeline.logging_utils import configure_logging

logger = logging.getLogger(__name__)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="미임베딩 청크 배치 임베딩 작업자")
    parser.add_argument(
        "--max-iterations", type=int, default=0,
        help="0이면 pending 이 없어질 때까지 반복(기본)",
    )
    parser.add_argument(
        "--log-file",
        help="로그 파일 경로(기본: embedding-data/job-posting/logs/embedding.log)",
    )
    args = parser.parse_args(argv)
    configure_logging("embedding", args.log_file)
    logger.info("임베딩 작업 시작: max_iterations=%d", args.max_iterations)

    settings = get_settings()
    if not settings.openai_api_key:
        logger.error("OPENAI_API_KEY 가 설정되지 않았습니다.")
        return 1

    with connection() as conn:
        stats = run_embedding_worker(conn, max_iterations=args.max_iterations)
    logger.info(
        "완료 processed=%d success=%d failed=%d skipped=%d",
        stats.processed, stats.success, stats.failed, stats.skipped,
    )
    return 0 if stats.failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

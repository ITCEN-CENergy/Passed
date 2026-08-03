"""CSV 적재 + 청크 생성/동기화 실행 진입점.

기존 FastAPI 서버(app/main.py)와 분리된 별도 스크립트.
사용: python -m job_posting_pipeline.run_loader <csv경로> [<csv경로> ...]
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

if __package__:
    from .chunk_sync import sync_posting
    from .chunker import build_chunks
    from .config import get_settings
    from .csv_loader import CSVRowError, LoadResult, fetch_posting, load_csv
    from .db import connection, init_schema
    from .extraction import extract
    from .logging_utils import configure_logging
else:
    # `python job_posting_pipeline/run_loader.py` 직접 실행도 지원한다.
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    from job_posting_pipeline.chunk_sync import sync_posting
    from job_posting_pipeline.chunker import build_chunks
    from job_posting_pipeline.config import get_settings
    from job_posting_pipeline.csv_loader import (
        CSVRowError,
        LoadResult,
        fetch_posting,
        load_csv,
    )
    from job_posting_pipeline.db import connection, init_schema
    from job_posting_pipeline.extraction import extract
    from job_posting_pipeline.logging_utils import configure_logging

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# 공고별 LLM 추출·청크 생성·DB 동기화
# ---------------------------------------------------------------------------
def _process_postings(conn, job_posting_ids: list[int]) -> None:
    """각 공고별로 LLM 추출 -> 청크 생성 -> 동기화(공고 단위 트랜잭션)."""
    settings = get_settings()
    total = len(job_posting_ids)
    succeeded = 0
    failed = 0
    logger.info("공고 청크 처리 시작: total=%d", total)
    for position, jpid in enumerate(job_posting_ids, start=1):
        logger.info(
            "공고 처리 중: %d/%d (%.1f%%) job_posting_id=%s",
            position,
            total,
            (position / total * 100) if total else 100,
            jpid,
        )
        try:
            posting = fetch_posting(conn, jpid)
            if posting is None:
                logger.warning("job_posting_id=%s 조회 실패(스킵)", jpid)
                conn.rollback()
                failed += 1
                continue
            # 구조화 추출 결과와 원문을 합쳐 모든 source_type 청크를 만든다.
            outcome = extract(posting, conn=conn)
            chunks = build_chunks(
                posting, outcome.tech_stacks, outcome.benefits,
                settings.chunk_max_tokens, settings.chunk_overlap_tokens,
            )
            sync_posting(conn, jpid, chunks)
            # 공고 하나가 완성된 경우에만 추출 캐시와 청크를 함께 확정한다.
            conn.commit()
            succeeded += 1
        except Exception as exc:  # noqa: BLE001 - 공고 단위 격리
            conn.rollback()
            failed += 1
            logger.error("공고 처리 실패 job_posting_id=%s: %s", jpid, exc)
    logger.info(
        "공고 청크 처리 완료: total=%d success=%d failed=%d",
        total,
        succeeded,
        failed,
    )


# ---------------------------------------------------------------------------
# CSV 파일 단위 전체 파이프라인
# ---------------------------------------------------------------------------
def run(csv_paths: list[str], init: bool = True) -> LoadResult:
    total = LoadResult()
    logger.info("CSV 적재 작업 시작: files=%d init_schema=%s", len(csv_paths), init)
    with connection() as conn:
        if init:
            init_schema(conn)
        for index, path in enumerate(csv_paths, start=1):
            logger.info("CSV 처리 시작: %d/%d path=%s", index, len(csv_paths), path)
            res = load_csv(conn, path)
            logger.info(
                "CSV 처리 결과: %d/%d path=%s loaded=%d failed=%d",
                index,
                len(csv_paths),
                path,
                res.loaded,
                res.failed,
            )
            total.loaded += res.loaded
            total.failed += res.failed
            total.failures.extend(res.failures)
            total.job_posting_ids.extend(res.job_posting_ids)
        # 원본 공고를 먼저 확정한 뒤 청크 작업을 공고별 트랜잭션으로 분리한다.
        conn.commit()
        logger.info(
            "CSV 전체 DB 적재 커밋 완료: loaded=%d failed=%d",
            total.loaded,
            total.failed,
        )
        # 청크 동기화는 공고 단위 트랜잭션
        _process_postings(conn, total.job_posting_ids)
    logger.info("CSV 적재 작업 종료")
    return total


# ---------------------------------------------------------------------------
# CLI 인자 처리
# ---------------------------------------------------------------------------
def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="CSV 적재 -> job_postings UPSERT -> 청크 생성/동기화"
    )
    parser.add_argument("csv", nargs="+", help="CSV 파일 경로(하나 이상)")
    parser.add_argument(
        "--no-init", action="store_true",
        help="스키마 초기화(job_posting_chunks 생성)를 건너뛴다",
    )
    parser.add_argument(
        "--log-file",
        help="로그 파일 경로(기본: embedding-data/job-posting/logs/loader.log)",
    )
    args = parser.parse_args(argv)
    configure_logging("loader", args.log_file)
    logger.info("명령 인자: csv_count=%d no_init=%s", len(args.csv), args.no_init)
    try:
        result = run(args.csv, init=not args.no_init)
    except (CSVRowError, FileNotFoundError) as exc:
        logger.error("CSV 적재 중단: %s", exc)
        return 1
    logger.info(
        "전체 적재 결과 loaded=%d failed=%d failures=%d",
        result.loaded, result.failed, len(result.failures),
    )
    if result.failures:
        for line_no, reason in result.failures[:20]:
            logger.warning("실패 행/ID %s: %s", line_no, reason)
    return 0 if result.failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

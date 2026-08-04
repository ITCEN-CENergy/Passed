"""사용자 문서 청킹 CLI: python -m resume_pipeline.run_chunking --user-id 1"""

from __future__ import annotations

import argparse
import logging

from .db import connection
from .pipeline import run_chunking_for_user


def main() -> None:
    parser = argparse.ArgumentParser(description="이력서·자기소개서 청크 동기화")
    parser.add_argument("--user-id", type=int, required=True)
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    with connection() as conn:
        result = run_chunking_for_user(conn, args.user_id)
    logging.info("청크 동기화 완료 user_id=%s result=%s", args.user_id, result)


if __name__ == "__main__":
    main()

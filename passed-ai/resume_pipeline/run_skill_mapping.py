"""실제 사용자의 추출 후보를 매핑·병합하고 선택적으로 DB에 동기화하는 CLI."""

from __future__ import annotations

import argparse
import logging
import os
from pathlib import Path
import sys

from .db import connection
from .skill_extraction_models import SkillExtractionReport
from .skill_extraction_worker import extract_user_skill_candidates
from .user_resolver import UserNotFoundError, resolve_user_id
from .user_skill_mapping_worker import (
    build_user_skill_mapping_report,
    persist_user_skill_mapping,
)


logger = logging.getLogger(__name__)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="실제 사용자 스킬 후보를 마스터에 매핑하고 근거·level을 병합"
    )
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--user-id", type=int)
    target.add_argument("--email")
    parser.add_argument(
        "--extraction-input",
        type=Path,
        help="기존 추출 JSON을 재사용하여 LLM 추출 호출을 생략",
    )
    parser.add_argument("--output", type=Path, help="preview/result JSON 저장 경로")
    parser.add_argument(
        "--persist",
        action="store_true",
        help="검증된 결과를 user_skills/user_skill_evidences에 트랜잭션 저장",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = _parser()
    args = parser.parse_args(argv)
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
            if args.extraction_input:
                extraction = SkillExtractionReport.model_validate_json(
                    args.extraction_input.read_text(encoding="utf-8")
                )
                if extraction.user_id != user_id:
                    parser.error(
                        "--extraction-input의 user_id와 실행 대상 사용자가 다릅니다."
                    )
            else:
                if not os.getenv("OPENAI_API_KEY"):
                    parser.error("실제 추출에는 OPENAI_API_KEY가 필요합니다.")
                extraction = extract_user_skill_candidates(conn, user_id)

            report = build_user_skill_mapping_report(conn, extraction)
            if args.persist:
                stats = persist_user_skill_mapping(conn, report)
                report = report.model_copy(
                    update={"persisted": True, "persist_stats": stats}
                )
    except (UserNotFoundError, ValueError) as exc:
        logger.error("사용자 스킬 매핑 중단: %s", exc)
        return 2

    output = report.model_dump_json(indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
        logger.info("사용자 스킬 매핑 결과 저장 path=%s", args.output.resolve())
    else:
        print(output, end="")
    logger.info(
        "사용자 스킬 매핑 완료 user_id=%s skills=%s unmapped=%s persisted=%s",
        report.user_id,
        len(report.skills),
        len(report.unmapped),
        report.persisted,
    )
    return 0 if not report.extraction_failures else 1


if __name__ == "__main__":
    sys.exit(main())

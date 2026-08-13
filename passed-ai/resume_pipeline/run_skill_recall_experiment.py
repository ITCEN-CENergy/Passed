"""실제 사용자 청크로 Top-K Retrieval과 선택적 Pass 2를 preview하는 CLI."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import sys

from .db import connection
from .skill_extraction_models import SkillExtractionReport
from .skill_recall_worker import (
    RecallExperimentReport,
    load_retrieval_expectations,
    retrieve_missing_master_candidates,
    verify_retrieval_with_pass2,
)
from .user_resolver import resolve_user_id
from .user_skill_mapping_models import UserSkillMappingReport
from .user_skill_mapping_worker import build_user_skill_mapping_report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="청크 embedding 기반 마스터 누락 후보 Retrieval/Pass 2 실험"
    )
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--user-id", type=int)
    target.add_argument("--email")
    parser.add_argument("--extraction-input", type=Path, required=True)
    parser.add_argument(
        "--mapping-input",
        type=Path,
        help=(
            "선택 사항: 기존 Pass 1 mapping preview JSON. 제공하면 후보명 임베딩 "
            "API 호출 없이 이미 매핑된 skill_id를 재사용합니다."
        ),
    )
    parser.add_argument(
        "--top-k",
        type=int,
        action="append",
        default=[],
        help="카테고리별 K. 비교하려면 --top-k 20 --top-k 40처럼 지정",
    )
    parser.add_argument(
        "--retrieval-mode",
        action="append",
        choices=("chunk", "sentence", "hybrid"),
        default=[],
        help=(
            "검색 단위. A/B/C 비교는 chunk, sentence, hybrid를 각각 지정"
        ),
    )
    parser.add_argument(
        "--sentence-top-k",
        type=int,
        default=5,
        help="sentence 모드에서 문장별·카테고리별로 먼저 가져올 후보 수",
    )
    parser.add_argument(
        "--expectations",
        type=Path,
        help="선택 사항: content_hash 기준 Retrieval 정답 JSON",
    )
    parser.add_argument(
        "--verify-top-k",
        type=int,
        help="지정한 K의 Retrieval 결과에만 Pass 2 LLM 검증 실행",
    )
    parser.add_argument(
        "--strict-pass2",
        action="store_true",
        help=(
            "Pass 2를 Precision 우선 strict evidence verifier로 실행하고 "
            "신규 스킬/추가 근거를 구분"
        ),
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    top_ks = sorted(set(args.top_k or [20, 40]))
    retrieval_modes = list(dict.fromkeys(args.retrieval_mode or ["chunk"]))
    if args.verify_top_k is not None and args.verify_top_k not in top_ks:
        parser.error("--verify-top-k 값은 --top-k 목록에 포함되어야 합니다.")
    if args.verify_top_k is not None and not os.getenv("OPENAI_API_KEY"):
        parser.error("Pass 2 검증에는 OPENAI_API_KEY가 필요합니다.")
    if ({"sentence", "hybrid"} & set(retrieval_modes)) and not os.getenv(
        "OPENAI_API_KEY"
    ):
        parser.error("sentence/hybrid Retrieval에는 OPENAI_API_KEY가 필요합니다.")
    if args.verify_top_k is not None and len(retrieval_modes) > 1:
        parser.error("Pass 2 실행 시 --retrieval-mode은 하나만 지정하세요.")
    if args.strict_pass2 and args.verify_top_k is None:
        parser.error("--strict-pass2에는 --verify-top-k가 필요합니다.")

    extraction = SkillExtractionReport.model_validate_json(
        args.extraction_input.read_text(encoding="utf-8")
    )
    expectations = (
        load_retrieval_expectations(args.expectations)
        if args.expectations
        else None
    )
    with connection() as conn:
        user_id = args.user_id or resolve_user_id(conn, args.email)
        if extraction.user_id != user_id:
            parser.error("추출 JSON의 user_id와 실행 대상 사용자가 다릅니다.")

        # Pass 1의 embedding 매핑까지 포함한 실제 결과를 빼야 이미 찾은 마스터를
        # Pass 2가 다시 제안하지 않습니다. 저장된 preview가 있으면 API를 재호출하지
        # 않고 재사용하며, 없을 때만 현재 매핑기를 읽기 전용으로 실행합니다.
        pass1_mapping = (
            UserSkillMappingReport.model_validate_json(
                args.mapping_input.read_text(encoding="utf-8")
            )
            if args.mapping_input
            else build_user_skill_mapping_report(conn, extraction)
        )
        if pass1_mapping.user_id != user_id:
            parser.error("mapping JSON의 user_id와 실행 대상 사용자가 다릅니다.")
        sentence_embedding_cache: dict[str, list[float]] = {}
        retrievals = [
            retrieve_missing_master_candidates(
                conn,
                extraction,
                pass1_mapping,
                top_k_per_category=top_k,
                expectations=expectations,
                retrieval_mode=mode,
                sentence_top_k=args.sentence_top_k,
                embedding_cache=sentence_embedding_cache,
                exclude_pass1_chunk_skills=not args.strict_pass2,
            )
            for mode in retrieval_modes
            for top_k in top_ks
        ]
        verification_source = next(
            (
                report
                for report in retrievals
                if report.top_k_per_category == args.verify_top_k
            ),
            None,
        )
        pass2 = (
            verify_retrieval_with_pass2(
                verification_source,
                strict=args.strict_pass2,
                pass1_mapping=pass1_mapping if args.strict_pass2 else None,
            )
            if verification_source is not None
            else None
        )

    report = RecallExperimentReport(
        extraction_model=extraction.model,
        pass1_skill_count=len(pass1_mapping.skills),
        pass1_unmapped_count=len(pass1_mapping.unmapped),
        retrievals=retrievals,
        pass2=pass2,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report.model_dump_json(indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())

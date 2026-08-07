"""COMPLETED 청크에서 매핑 전 스킬 후보를 구조화 출력으로 추출한다."""

from __future__ import annotations

import logging
import os
import re
from typing import Any

from tenacity import (
    Retrying,
    before_sleep_log,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
)

from .skill_extraction_models import (
    ExtractableChunk,
    ExtractedChunkSkills,
    FailedChunkExtraction,
    SkillCandidate,
    SkillExtractionReport,
    SkillExtractionResponse,
)
from .skill_extraction_prompt import SYSTEM_PROMPT, build_user_prompt


logger = logging.getLogger(__name__)

SKILL_EXTRACTION_MODEL = os.getenv("SKILL_EXTRACTION_MODEL", "gpt-4o-mini")
SKILL_EXTRACTION_MAX_RETRIES = int(
    os.getenv("SKILL_EXTRACTION_MAX_RETRIES", "3")
)
SKILL_EXTRACTION_TIMEOUT_SECONDS = float(
    os.getenv("SKILL_EXTRACTION_TIMEOUT_SECONDS", "60")
)


class TransientSkillExtractionError(RuntimeError):
    """호출 제한·연결·timeout·서버 오류처럼 재시도 가능한 오류."""


class InvalidSkillExtractionResponse(RuntimeError):
    """구조화 응답이 없거나 원문 근거 계약을 위반했을 때 사용한다."""


_FUTURE_ONLY_PATTERNS = tuple(
    re.compile(pattern)
    for pattern in (
        r"고 싶(?:습니다|다)",
        r"(?:하|되|만들|기여|성장|도전)겠습니다",
        r"(?:할|될) (?:계획|예정|목표)입니다",
    )
)
_COMPLETED_ACTION_PATTERN = re.compile(
    r"(?:했|하였다|했습니다|하였습니다|했으며|했고|했음|함(?:[.\s]|$)|"
    r"맡아|담당하여|수행하여|개발하여|구현하여|개선하여|운영하여|분석하여|"
    r"조율하여|해결하여|적용하여|설계하여|작성하여|리딩하여|참여하여)"
)


def _is_future_only_evidence(evidence: str) -> bool:
    """과거 수행 근거 없이 희망·계획만 말하는 evidence인지 판별한다."""
    has_future_expression = any(
        pattern.search(evidence) for pattern in _FUTURE_ONLY_PATTERNS
    )
    return has_future_expression and not _COMPLETED_ACTION_PATTERN.search(evidence)


def create_skill_extraction_client() -> Any:
    import openai

    return openai.OpenAI(
        api_key=os.getenv("OPENAI_API_KEY"),
        timeout=SKILL_EXTRACTION_TIMEOUT_SECONDS,
        max_retries=0,
    )


def _request_structured_extraction(client: Any, chunk: ExtractableChunk) -> Any:
    import openai

    try:
        return client.responses.parse(
            model=SKILL_EXTRACTION_MODEL,
            input=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": build_user_prompt(chunk)},
            ],
            text_format=SkillExtractionResponse,
        )
    except (
        openai.RateLimitError,
        openai.APITimeoutError,
        openai.APIConnectionError,
        openai.InternalServerError,
    ) as exc:
        raise TransientSkillExtractionError(str(exc)) from exc


def _validated_candidates(
    chunk: ExtractableChunk,
    response: SkillExtractionResponse,
) -> list[SkillCandidate]:
    """원문 evidence가 있는 후보만 남기고 미래 포부와 중복을 제거한다."""
    result: list[SkillCandidate] = []
    seen: set[tuple[str, str]] = set()
    for candidate in response.skills:
        if candidate.evidence not in chunk.chunk_content:
            logger.warning(
                "원문에 없는 스킬 근거 제외 source=%s chunk_id=%s "
                "extracted_name=%r evidence=%r",
                chunk.source_kind,
                chunk.chunk_id,
                candidate.extracted_name,
                candidate.evidence,
            )
            continue

        if _is_future_only_evidence(candidate.evidence):
            logger.warning(
                "미래 포부만 근거인 스킬 후보 제외 source=%s chunk_id=%s "
                "extracted_name=%r evidence=%r",
                chunk.source_kind,
                chunk.chunk_id,
                candidate.extracted_name,
                candidate.evidence,
            )
            continue

        key = (
            " ".join(candidate.extracted_name.casefold().split()),
            candidate.category.value,
        )
        if key in seen:
            continue
        seen.add(key)
        result.append(candidate)
    return result


def extract_chunk_candidates(
    chunk: ExtractableChunk,
    *,
    client: Any | None = None,
) -> list[SkillCandidate]:
    """청크 한 건을 최대 3회 재시도해 검증된 후보 목록으로 반환한다."""
    api_client = client or create_skill_extraction_client()
    response = Retrying(
        retry=retry_if_exception_type(TransientSkillExtractionError),
        stop=stop_after_attempt(SKILL_EXTRACTION_MAX_RETRIES),
        wait=wait_exponential(multiplier=1, min=1, max=10),
        before_sleep=before_sleep_log(logger, logging.WARNING),
        reraise=True,
    )(_request_structured_extraction, api_client, chunk)

    parsed = getattr(response, "output_parsed", None)
    if parsed is None:
        raise InvalidSkillExtractionResponse(
            f"구조화된 output_parsed가 없습니다: chunk_id={chunk.chunk_id}"
        )
    if not isinstance(parsed, SkillExtractionResponse):
        parsed = SkillExtractionResponse.model_validate(parsed)
    return _validated_candidates(chunk, parsed)


def _chunk_from_row(row: Any, source_kind: str) -> ExtractableChunk:
    if isinstance(row, dict):
        return ExtractableChunk(
            source_kind=source_kind,
            chunk_id=int(row["chunk_id"]),
            context_type=str(row["context_type"]),
            chunk_content=str(row["chunk_content"]),
            content_hash=str(row["content_hash"]),
        )
    return ExtractableChunk(
        source_kind=source_kind,
        chunk_id=int(row[0]),
        context_type=str(row[1]),
        chunk_content=str(row[2]),
        content_hash=str(row[3]),
    )


def load_extractable_chunks(conn: Any, user_id: int) -> list[ExtractableChunk]:
    """사용자의 임베딩 완료 이력서·자기소개서 청크를 문맥 정보와 함께 읽는다."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT rc.id AS chunk_id, rc.source_type AS context_type, "
            "rc.chunk_content, rc.content_hash "
            "FROM resume_chunks rc "
            "JOIN resumes r ON r.id = rc.resume_id "
            "WHERE r.user_id = %s AND rc.embedding_status = 'COMPLETED' "
            "AND rc.embedding IS NOT NULL ORDER BY rc.id",
            (user_id,),
        )
        resume_chunks = [
            _chunk_from_row(row, "RESUME") for row in cur.fetchall()
        ]

        cur.execute(
            "SELECT cc.id AS chunk_id, q.question_type AS context_type, "
            "cc.chunk_content, cc.content_hash "
            "FROM cover_letter_chunks cc "
            "JOIN cover_letter_items ci ON ci.id = cc.cover_letter_item_id "
            "JOIN cover_letters cl ON cl.id = ci.cover_letter_id "
            "JOIN cover_letter_questions q ON q.id = ci.question_id "
            "WHERE cl.user_id = %s AND cc.embedding_status = 'COMPLETED' "
            "AND cc.embedding IS NOT NULL ORDER BY cc.id",
            (user_id,),
        )
        cover_chunks = [
            _chunk_from_row(row, "COVER_LETTER") for row in cur.fetchall()
        ]

    return [*resume_chunks, *cover_chunks]


def extract_user_skill_candidates(
    conn: Any,
    user_id: int,
    *,
    client: Any | None = None,
) -> SkillExtractionReport:
    """청크별 실패를 격리하면서 사용자의 스킬 후보 보고서를 만든다."""
    chunks = load_extractable_chunks(conn, user_id)
    api_client = client or (create_skill_extraction_client() if chunks else None)
    extracted: list[ExtractedChunkSkills] = []
    failures: list[FailedChunkExtraction] = []

    for chunk in chunks:
        try:
            skills = extract_chunk_candidates(chunk, client=api_client)
            extracted.append(
                ExtractedChunkSkills(
                    source_kind=chunk.source_kind,
                    chunk_id=chunk.chunk_id,
                    context_type=chunk.context_type,
                    content_hash=chunk.content_hash,
                    skills=skills,
                )
            )
            logger.info(
                "스킬 후보 추출 완료 source=%s chunk_id=%s candidates=%s",
                chunk.source_kind,
                chunk.chunk_id,
                len(skills),
            )
        except Exception as exc:  # noqa: BLE001 - 한 청크 실패가 다음 청크를 막지 않게 격리
            failures.append(
                FailedChunkExtraction(
                    source_kind=chunk.source_kind,
                    chunk_id=chunk.chunk_id,
                    error=f"{type(exc).__name__}: {exc}",
                )
            )
            logger.exception(
                "스킬 후보 추출 실패 source=%s chunk_id=%s",
                chunk.source_kind,
                chunk.chunk_id,
            )

    return SkillExtractionReport(
        user_id=user_id,
        model=SKILL_EXTRACTION_MODEL,
        chunks=extracted,
        failures=failures,
    )

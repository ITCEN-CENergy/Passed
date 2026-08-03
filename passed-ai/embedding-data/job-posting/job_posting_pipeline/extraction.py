"""LLM 기반 구조화 추출(계획서 10절).

title/position_detail/main_duty/qualification/preference 를 입력으로
기술 스택과 복리후생을 JSON 스키마 보장 구조화 출력으로 추출한다.
원문에 없는 항목은 폐기하고, 별칭·대소문자를 표준 이름으로 통합한다.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Any

from pydantic import BaseModel, Field

from .config import get_settings
from .models import ExtractedItem

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# LLM 구조화 출력 스키마
# ---------------------------------------------------------------------------
class TechStackItem(BaseModel):
    name: str
    evidence: str


class BenefitItem(BaseModel):
    name: str
    evidence: str


class ExtractionResult(BaseModel):
    """LLM 구조화 출력 스키마."""

    tech_stacks: list[TechStackItem] = Field(default_factory=list)
    benefits: list[BenefitItem] = Field(default_factory=list)


@dataclass
class ExtractionOutcome:
    tech_stacks: list[ExtractedItem]
    benefits: list[ExtractedItem]
    from_cache: bool
    used_model: str
    used_prompt_version: str


# 프롬프트는 원문에 없는 사실을 추측하지 못하도록 역할과 제약을 고정한다.
_SYSTEM_PROMPT = (
    "너는 채용공고 분석 보조다. 입력 공고 원문에서 기술 스택과 복리후생을 "
    "추출한다. 입력 원문에 명시된 정보만 반환하고, 일반적인 업계 관행이나 "
    "회사 복지를 추측하지 마라. 없으면 빈 배열을 반환하라. "
 "모든 추출 항목에는 원문에 실제로 등장하는 근거 문장(evidence)을 반드시 "
    "함께 제시하라."
)

_USER_PROMPT_TEMPLATE = (
    "아래 채용공고에서 요구/우대 기술 스택과 복리후생을 추출하라.\n"
    "규칙:\n"
    "- 원문에 명시된 정보만 반환할 것.\n"
    "- 추측 금지.\n"
    "- 없으면 빈 배열.\n"
    "- 각 항목의 evidence 는 원문에 존재하는 문장/구여야 한다.\n\n"
    "제목: {title}\n"
    "포지션 상세: {position_detail}\n"
    "주요 업무: {main_duty}\n"
    "자격 요건: {qualification}\n"
    "우대 요건: {preference}\n"
)


# ---------------------------------------------------------------------------
# 프롬프트 입력과 변경 감지 해시
# ---------------------------------------------------------------------------
def _build_user_prompt(posting: dict) -> str:
    return _USER_PROMPT_TEMPLATE.format(
        title=posting.get("title") or "",
        position_detail=posting.get("position_detail") or "",
        main_duty=posting.get("main_duty") or "",
        qualification=posting.get("qualification") or "",
        preference=posting.get("preference") or "",
    )


def _input_hash(posting: dict) -> str:
    import hashlib

    payload = json.dumps(
        {
            "title": posting.get("title") or "",
            "position_detail": posting.get("position_detail") or "",
            "main_duty": posting.get("main_duty") or "",
            "qualification": posting.get("qualification") or "",
            "preference": posting.get("preference") or "",
        },
        ensure_ascii=False,
        sort_keys=True,
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def _validate_evidence(items: list, source_text: str, label: str) -> list[ExtractedItem]:
    """evidence 가 원문에 존재하지 않으면 폐기하고 로그를 남긴다."""
    kept: list[ExtractedItem] = []
    for item in items:
        ev = (item.evidence or "").strip()
        name = (item.name or "").strip()
        if not name:
            continue
        # LLM의 추측 저장을 막기 위해 evidence가 원문에 정확히 포함될 때만 인정한다.
        if ev and ev in source_text:
            kept.append(ExtractedItem(name=name, evidence=ev))
        else:
            logger.warning(
                "추출 폐기(%s): name=%r evidence 가 원문에 없음", label, name
            )
    return kept


# ---------------------------------------------------------------------------
# 입력 해시 기반 추출 캐시
# ---------------------------------------------------------------------------
def _load_from_cache(conn, job_posting_id: int, input_h: str, prompt_version: str) -> ExtractionOutcome | None:
    """입력 해시가 같으면 이전 추출 결과를 재사용(LLM 호출 생략)."""
    if conn is None:
        return None
    table = get_settings().extraction_cache_table
    with conn.cursor() as cur:
        cur.execute(
            f"SELECT input_hash, prompt_version, extraction_model, "
            f"tech_stacks_json, benefits_json FROM {table} "
            f"WHERE job_posting_id = %s",
            (job_posting_id,),
        )
        row = cur.fetchone()
    if row is None:
        return None
    db_input_hash, db_prompt_version, db_model, tech_json, benefit_json = row
    # 원문 또는 프롬프트 버전이 바뀌면 이전 결과를 사용하지 않는다.
    if db_input_hash != input_h or db_prompt_version != prompt_version:
        return None
    tech = [ExtractedItem(name=d["name"], evidence=d.get("evidence", "")) for d in tech_json]
    benefit = [ExtractedItem(name=d["name"], evidence=d.get("evidence", "")) for d in benefit_json]
    return ExtractionOutcome(
        tech_stacks=tech,
        benefits=benefit,
        from_cache=True,
        used_model=db_model,
        used_prompt_version=db_prompt_version,
    )


def _save_cache(
    conn,
    job_posting_id: int,
    input_h: str,
    prompt_version: str,
    model: str,
    outcome: ExtractionOutcome,
) -> None:
    if conn is None:
        return
    table = get_settings().extraction_cache_table
    tech_json = json.dumps(
        [{"name": i.name, "evidence": i.evidence} for i in outcome.tech_stacks],
        ensure_ascii=False,
    )
    benefit_json = json.dumps(
        [{"name": i.name, "evidence": i.evidence} for i in outcome.benefits],
        ensure_ascii=False,
    )
    with conn.cursor() as cur:
        cur.execute(
            f"INSERT INTO {table} "
            f"(job_posting_id, input_hash, prompt_version, extraction_model, "
            f"tech_stacks_json, benefits_json) VALUES (%s,%s,%s,%s,%s,%s) "
            f"ON CONFLICT (job_posting_id) DO UPDATE SET "
            f"input_hash = EXCLUDED.input_hash, "
            f"prompt_version = EXCLUDED.prompt_version, "
            f"extraction_model = EXCLUDED.extraction_model, "
            f"tech_stacks_json = EXCLUDED.tech_stacks_json, "
            f"benefits_json = EXCLUDED.benefits_json",
            (job_posting_id, input_h, prompt_version, model, tech_json, benefit_json),
        )


# ---------------------------------------------------------------------------
# 외부 LLM 호출
# ---------------------------------------------------------------------------
def _extract_with_llm(posting: dict) -> ExtractionResult:
    """langchain-openai 구조화 출력으로 추출."""
    from langchain_openai import ChatOpenAI

    settings = get_settings()
    llm = ChatOpenAI(model=settings.extraction_model, temperature=0)
    structured = llm.with_structured_output(ExtractionResult)
    user_prompt = _build_user_prompt(posting)
    result = structured.invoke([("system", _SYSTEM_PROMPT), ("user", user_prompt)])
    if isinstance(result, BaseModel):
        return result  # type: ignore[return-value]
    # 일부 버전은 dict 반환
    return ExtractionResult.model_validate(result)


def extract(
    posting: dict,
    conn=None,
) -> ExtractionOutcome:
    """공고에서 기술 스택·복리후생을 추출.

    - OPENAI_API_KEY 가 없거나 EXTRACT_WITH_LLM=false 면 빈 결과 반환.
    - 입력 해시 캐시가 같으면 LLM 호출을 생략한다.
    - evidence 검증 후 폐기/로그 처리.
    """
    settings = get_settings()
    source_text = "\n".join(
        [
            posting.get("title") or "",
            posting.get("position_detail") or "",
            posting.get("main_duty") or "",
            posting.get("qualification") or "",
            posting.get("preference") or "",
        ]
    )
    input_h = _input_hash(posting)

    # API 호출 전에 캐시를 조회해 동일 원문의 중복 비용을 피한다.
    cached = _load_from_cache(conn, posting["id"], input_h, settings.extraction_prompt_version)
    if cached is not None:
        logger.info("추출 캐시 재사용 job_posting_id=%s", posting["id"])
        return cached

    empty = ExtractionOutcome(
        tech_stacks=[],
        benefits=[],
        from_cache=False,
        used_model=settings.extraction_model,
        used_prompt_version=settings.extraction_prompt_version,
    )

    if not settings.extract_with_llm or not settings.openai_api_key:
        logger.info("LLM 추출 생략 job_posting_id=%s", posting.get("id"))
        return empty

    last_err: Exception | None = None
    # 공고 하나가 반복 실패해도 전체 배치를 멈추지 않고 빈 추출 결과로 계속한다.
    for attempt in range(1, settings.extraction_max_retries + 1):
        try:
            raw: ExtractionResult = _extract_with_llm(posting)
            tech = _validate_evidence(raw.tech_stacks, source_text, "tech_stack")
            benefit = _validate_evidence(raw.benefits, source_text, "benefit")
            outcome = ExtractionOutcome(
                tech_stacks=tech,
                benefits=benefit,
                from_cache=False,
                used_model=settings.extraction_model,
                used_prompt_version=settings.extraction_prompt_version,
            )
            _save_cache(conn, posting["id"], input_h, settings.extraction_prompt_version, settings.extraction_model, outcome)
            logger.info(
                "추출 완료 job_posting_id=%s tech=%d benefit=%d model=%s prompt=%s",
                posting["id"], len(tech), len(benefit),
                settings.extraction_model, settings.extraction_prompt_version,
            )
            return outcome
        except Exception as exc:  # noqa: BLE001
            last_err = exc
            logger.warning(
                "추출 시도 %d/%d 실패 job_posting_id=%s: %s",
                attempt, settings.extraction_max_retries, posting.get("id"), exc,
            )

    logger.error(
        "추출 최종 실패 job_posting_id=%s: %s", posting.get("id"), last_err
    )
    return empty


def to_jsonable(outcome: ExtractionOutcome) -> dict[str, Any]:
    """캐시 저장용 직렬화 helper."""
    return {
        "tech_stacks": [{"name": i.name, "evidence": i.evidence} for i in outcome.tech_stacks],
        "benefits": [{"name": i.name, "evidence": i.evidence} for i in outcome.benefits],
    }

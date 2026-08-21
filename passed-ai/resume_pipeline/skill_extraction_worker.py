"""COMPLETED 청크에서 매핑 전 스킬 후보를 구조화 출력으로 추출한다."""

from __future__ import annotations

from difflib import SequenceMatcher
import logging
import os
import re
import unicodedata
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
    CoverLetterSkillExtractionResponse,
    ResumeSkillExtractionResponse,
    SkillCandidate,
    SkillCategory,
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
_SOURCE_SENTENCE_PATTERN = re.compile(r"[^.!?\n]+(?:[.!?]+|$)")
_GROUNDING_NOISE_PATTERN = re.compile(r"[^0-9a-zA-Z가-힣]+")
_MIN_RECOVERABLE_EVIDENCE_LENGTH = 12
_MIN_GROUNDING_COVERAGE = 0.72
_MIN_GROUNDING_RATIO = 0.55

# LLM이 실행마다 누락해도 원문에 완료 행동이 명시된 경우에만 복구하는 고신뢰 규칙입니다.
# 공고 문구가 아니라 여러 채용 문서에 공통으로 적용 가능한 수행 표현만 둡니다.
_EXPLICIT_COMPLETED_SKILL_RULES = (
    (
        "콘텐츠 생성",
        SkillCategory.TECHNICAL_SKILL,
        re.compile(r"콘텐츠 생성.{0,100}(?:개발|구현|연동)"),
    ),
    (
        "콘텐츠 생성 프로젝트",
        SkillCategory.EXPERIENCE,
        re.compile(
            r"(?:콘텐츠 생성.{0,80}(?:플랫폼|서비스)|"
            r"(?:플랫폼|서비스).{0,80}콘텐츠 생성).{0,100}(?:개발|구현)"
        ),
    ),
    (
        "우선순위 설정",
        SkillCategory.BEHAVIORAL_TRAIT,
        re.compile(r"우선순위.{0,60}(?:조율|정하|결정|반영)"),
    ),
    (
        "사용자 피드백 반영",
        SkillCategory.EXPERIENCE,
        re.compile(r"사용자 피드백.{0,80}(?:반영|개선)"),
    ),
    (
        "장애 재발 방지",
        SkillCategory.EXPERIENCE,
        re.compile(r"(?:장애.{0,80})?재발 방지"),
    ),
    (
        "평가 로직 개발",
        SkillCategory.EXPERIENCE,
        re.compile(r"평가 로직.{0,60}(?:개발|구현)"),
    ),
    (
        "AI 챗봇",
        SkillCategory.TECHNICAL_SKILL,
        re.compile(r"AI\s*챗봇.{0,120}(?:개발|구현|운영)"),
    ),
    (
        "LLM",
        SkillCategory.TECHNICAL_SKILL,
        re.compile(
            r"LLM(?!\s*API)(?:을|를|으로|에).{0,120}"
            r"(?:개발|구현|설계|연동|전달)"
        ),
    ),
    (
        "AI 챗봇 프로젝트",
        SkillCategory.EXPERIENCE,
        re.compile(r"AI\s*챗봇\s*프로젝트.{0,120}(?:개발|구현|수행)"),
    ),
    (
        "추천 서비스",
        SkillCategory.TECHNICAL_SKILL,
        re.compile(r"추천 서비스.{0,120}(?:개발|구현|운영)"),
    ),
    (
        "추천 서비스 프로젝트",
        SkillCategory.EXPERIENCE,
        re.compile(r"추천 서비스\s*프로젝트.{0,140}(?:개발|구현|수행)"),
    ),
    (
        "업무 자동화",
        SkillCategory.TECHNICAL_SKILL,
        re.compile(r"업무 자동화.{0,120}(?:개발|구현|적용)"),
    ),
    (
        "업무 자동화 프로젝트",
        SkillCategory.EXPERIENCE,
        re.compile(r"업무 자동화\s*프로젝트.{0,140}(?:개발|구현|수행)"),
    ),
    (
        "멀티모달 앱",
        SkillCategory.TECHNICAL_SKILL,
        re.compile(r"멀티모달 앱.{0,120}(?:개발|구현|운영)"),
    ),
    (
        "멀티모달 앱 프로젝트",
        SkillCategory.EXPERIENCE,
        re.compile(r"멀티모달 앱\s*프로젝트.{0,140}(?:개발|구현|수행)"),
    ),
    (
        "클라우드",
        SkillCategory.TECHNICAL_SKILL,
        re.compile(
            r"(?:AWS\s+)?클라우드 환경.{0,120}(?:배포|운영|개발|구현)"
        ),
    ),
    (
        "보안",
        SkillCategory.TECHNICAL_SKILL,
        re.compile(
            r"(?:보안 (?:기준|정책|요건).{0,100}(?:준수|적용|점검|관리)|"
            r"(?:준수|적용|점검|관리).{0,100}보안 (?:기준|정책|요건))"
        ),
    ),
    (
        "개인정보 보호",
        SkillCategory.TECHNICAL_SKILL,
        re.compile(r"개인정보 보호.{0,100}(?:준수|적용|점검|관리)"),
    ),
    (
        "정보보호 의식",
        SkillCategory.BEHAVIORAL_TRAIT,
        re.compile(
            r"정보보호 의식.{0,120}(?:바탕|준수|적용|점검|관리)"
        ),
    ),
)


def _is_future_only_evidence(evidence: str) -> bool:
    """과거 수행 근거 없이 희망·계획만 말하는 evidence인지 판별한다."""
    has_future_expression = any(
        pattern.search(evidence) for pattern in _FUTURE_ONLY_PATTERNS
    )
    return has_future_expression and not _COMPLETED_ACTION_PATTERN.search(evidence)


def _normalize_grounding_text(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return _GROUNDING_NOISE_PATTERN.sub("", normalized)


def _recover_verbatim_evidence(chunk_content: str, evidence: str) -> str | None:
    """LLM이 어미·문장부호만 바꾼 근거를 가장 가까운 원문 문장으로 복구한다."""
    if evidence in chunk_content:
        return evidence

    requested = _normalize_grounding_text(evidence)
    if len(requested) < _MIN_RECOVERABLE_EVIDENCE_LENGTH:
        return None

    best: tuple[float, float, str] | None = None
    for match in _SOURCE_SENTENCE_PATTERN.finditer(chunk_content):
        sentence = match.group(0).strip()
        source = _normalize_grounding_text(sentence)
        if not source:
            continue
        matcher = SequenceMatcher(None, requested, source, autojunk=False)
        longest = matcher.find_longest_match()
        coverage = longest.size / len(requested)
        ratio = matcher.ratio()
        candidate = (coverage, ratio, sentence)
        if best is None or candidate[:2] > best[:2]:
            best = candidate

    if best is None:
        return None
    coverage, ratio, sentence = best
    if coverage < _MIN_GROUNDING_COVERAGE or ratio < _MIN_GROUNDING_RATIO:
        return None
    return sentence


def _explicit_completed_candidates(
    chunk: ExtractableChunk,
    *,
    disabled_recovery_rules: frozenset[str] = frozenset(),
) -> list[SkillCandidate]:
    candidates: list[SkillCandidate] = []
    for sentence_match in _SOURCE_SENTENCE_PATTERN.finditer(chunk.chunk_content):
        sentence = sentence_match.group(0).strip()
        if not sentence or _is_future_only_evidence(sentence):
            continue
        for extracted_name, category, pattern in _EXPLICIT_COMPLETED_SKILL_RULES:
            if extracted_name in disabled_recovery_rules:
                continue
            if not pattern.search(sentence):
                continue
            candidates.append(
                SkillCandidate(
                    extracted_name=extracted_name,
                    category=category,
                    level=2,
                    evidence=sentence,
                )
            )
    return candidates


def create_skill_extraction_client() -> Any:
    import openai

    return openai.OpenAI(
        api_key=os.getenv("OPENAI_API_KEY"),
        timeout=SKILL_EXTRACTION_TIMEOUT_SECONDS,
        max_retries=0,
    )


def _response_model_for(chunk: ExtractableChunk) -> type[SkillExtractionResponse]:
    if chunk.source_kind == "RESUME":
        return ResumeSkillExtractionResponse
    if chunk.source_kind == "COVER_LETTER":
        return CoverLetterSkillExtractionResponse
    raise ValueError(f"지원하지 않는 문서 종류입니다: {chunk.source_kind}")


def _request_structured_extraction(client: Any, chunk: ExtractableChunk) -> Any:
    import openai

    try:
        return client.responses.parse(
            model=SKILL_EXTRACTION_MODEL,
            input=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": build_user_prompt(chunk)},
            ],
            text_format=_response_model_for(chunk),
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
    response: SkillExtractionResponse | CoverLetterSkillExtractionResponse,
    *,
    disabled_recovery_rules: frozenset[str] = frozenset(),
    enable_recovery_rules: bool = False,
) -> list[SkillCandidate]:
    """원문 evidence가 있는 후보만 남기고 미래 포부와 중복을 제거한다."""
    result: list[SkillCandidate] = []
    seen: set[tuple[str, str]] = set()
    for candidate in response.skills:
        grounded_evidence = _recover_verbatim_evidence(
            chunk.chunk_content,
            candidate.evidence,
        )
        if grounded_evidence is None:
            logger.warning(
                "원문에 없는 스킬 근거 제외 source=%s chunk_id=%s "
                "extracted_name=%r evidence=%r",
                chunk.source_kind,
                chunk.chunk_id,
                candidate.extracted_name,
                candidate.evidence,
            )
            continue

        if grounded_evidence != candidate.evidence:
            logger.info(
                "스킬 근거를 원문 문장으로 복구 source=%s chunk_id=%s "
                "extracted_name=%r",
                chunk.source_kind,
                chunk.chunk_id,
                candidate.extracted_name,
            )
            candidate = candidate.model_copy(
                update={"evidence": grounded_evidence}
            )

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

    # Q. 기존 deterministic recovery를 왜 삭제하지 않고 호출만 끄나요?
    # A. 저장 경로에서는 사용하지 않되, 기존 baseline을 재현할 수 있도록 코드는 잠시
    #    보존합니다. 새 Pass 1 + Retrieval Pass 2 회귀가 끝나면 규칙 자체를 제거합니다.
    if enable_recovery_rules:
        for candidate in _explicit_completed_candidates(
            chunk,
            disabled_recovery_rules=disabled_recovery_rules,
        ):
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
    disabled_recovery_rules: frozenset[str] = frozenset(),
    enable_recovery_rules: bool = False,
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
    response_model = _response_model_for(chunk)
    if not isinstance(parsed, response_model):
        parsed = response_model.model_validate(
            parsed.model_dump() if hasattr(parsed, "model_dump") else parsed
        )
    return _validated_candidates(
        chunk,
        parsed,
        disabled_recovery_rules=disabled_recovery_rules,
        enable_recovery_rules=enable_recovery_rules,
    )


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

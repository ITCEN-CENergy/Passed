"""청크 임베딩으로 마스터 후보를 검색하고 제한된 Pass 2로 누락을 검증한다."""

from __future__ import annotations

from collections import defaultdict
from pathlib import Path
import re
from typing import Any, Literal
import unicodedata

from pydantic import BaseModel, ConfigDict, Field

from .embedding_worker import (
    EMBEDDING_BATCH_SIZE,
    _create_embeddings,
    _create_openai_client,
)
from .skill_extraction_models import SkillCategory, SkillExtractionReport
from .skill_extraction_worker import (
    SKILL_EXTRACTION_MODEL,
    _COMPLETED_ACTION_PATTERN,
    _is_future_only_evidence,
    _normalize_grounding_text,
    _recover_verbatim_evidence,
    create_skill_extraction_client,
)
from .user_skill_mapping_models import UserSkillMappingReport


PASS2_CATEGORIES = frozenset(
    {SkillCategory.TECHNICAL_SKILL, SkillCategory.BEHAVIORAL_TRAIT}
)


class MasterRetrievalHit(BaseModel):
    skill_id: int
    name: str
    category: SkillCategory
    description: str
    similarity: float
    retrieval_source: str = "chunk"
    aliases: list[str] = Field(default_factory=list)
    matched_sentence: str | None = None
    sentence_index: int | None = None


class ChunkRetrieval(BaseModel):
    source_kind: str
    chunk_id: int
    context_type: str
    content_hash: str
    chunk_content: str
    excluded_pass1_skill_ids: list[int] = Field(default_factory=list)
    candidates: list[MasterRetrievalHit] = Field(default_factory=list)


class RetrievalExpectation(BaseModel):
    model_config = ConfigDict(extra="forbid")
    content_hash: str
    skill_name: str
    category: SkillCategory


class RetrievalMetric(BaseModel):
    expected_count: int = 0
    found_count: int = 0
    recall_at_k: float = 0.0
    by_category: dict[str, float] = Field(default_factory=dict)


class RetrievalReport(BaseModel):
    user_id: int
    retrieval_mode: str = "chunk"
    top_k_per_category: int
    categories: list[SkillCategory]
    chunks: list[ChunkRetrieval]
    metric: RetrievalMetric | None = None


class Pass2Selection(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    skill_id: int
    evidence: str = Field(min_length=1, max_length=500)
    level: int = Field(ge=1, le=3)


class Pass2Response(BaseModel):
    model_config = ConfigDict(extra="forbid")
    verified: list[Pass2Selection] = Field(default_factory=list)


class StrictPass2Selection(BaseModel):
    """Strict verifier가 승인 근거를 명시적으로 설명하는 출력 계약."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    skill_id: int
    evidence: str = Field(min_length=1, max_length=500)
    observable_action: str | None = Field(
        default=None,
        max_length=500,
        description=(
            "BEHAVIORAL_TRAIT을 직접 입증하는 원문의 연속된 완료 행동. "
            "기술/자격 직접 명시 검증에서는 null 허용"
        ),
    )
    verification_basis: Literal[
        "DIRECT_NAME_OR_ALIAS",
        "OBSERVABLE_COMPLETED_ACTION",
    ]
    level: int = Field(ge=1, le=3)


class StrictPass2Response(BaseModel):
    model_config = ConfigDict(extra="forbid")
    verified: list[StrictPass2Selection] = Field(default_factory=list)


class BehavioralDirectnessDecision(BaseModel):
    """행동과 행동 특성 사이에 추가 추론이 필요한지 재검증한 결과."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    skill_id: int
    accept: bool
    requires_trait_inference: bool
    directly_matches_definition: bool
    mere_related_action: bool
    missing_definition_elements: list[str] = Field(default_factory=list)
    reason: Literal[
        "DIRECT_OBSERVABLE_ACTION",
        "INFERRED_FROM_RELATED_ACTION",
        "INSUFFICIENT_BEHAVIOR_STRUCTURE",
        "SELF_ASSESSMENT_OR_INTENT",
    ]


class BehavioralDirectnessResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")
    decisions: list[BehavioralDirectnessDecision] = Field(default_factory=list)


class VerifiedMissingSkill(BaseModel):
    skill_id: int
    name: str
    category: SkillCategory
    evidence: str
    level: int = Field(ge=1, le=3)
    retrieval_similarity: float
    recovery_type: str | None = None


class ChunkPass2Result(BaseModel):
    source_kind: str
    chunk_id: int
    content_hash: str
    proposed_count: int
    verified: list[VerifiedMissingSkill] = Field(default_factory=list)
    rejected_invalid_ids: list[int] = Field(default_factory=list)
    rejected_ungrounded_ids: list[int] = Field(default_factory=list)
    rejected_direct_mention_ids: list[int] = Field(default_factory=list)
    rejected_duplicate_ids: list[int] = Field(default_factory=list)
    rejected_intent_ids: list[int] = Field(default_factory=list)
    rejected_context_ids: list[int] = Field(default_factory=list)
    rejected_behavioral_generic_ids: list[int] = Field(default_factory=list)
    rejected_behavioral_directness_ids: list[int] = Field(default_factory=list)
    rejected_observable_action_ids: list[int] = Field(default_factory=list)


class Pass2PreviewReport(BaseModel):
    model: str
    verifier_mode: str = "semantic"
    chunks: list[ChunkPass2Result]

    @property
    def verified_count(self) -> int:
        return sum(len(chunk.verified) for chunk in self.chunks)


class RecallExperimentReport(BaseModel):
    extraction_model: str
    pass1_skill_count: int
    pass1_unmapped_count: int
    retrievals: list[RetrievalReport]
    pass2: Pass2PreviewReport | None = None


_RETRIEVAL_SENTENCE_PATTERN = re.compile(r"[^.!?\n]+(?:[.!?]+|$)")


def _split_retrieval_sentences(content: str) -> list[str]:
    """DB 청크는 유지하고 Retrieval 요청을 위한 임시 문장만 만든다.

    Q. 문장들을 resume_chunks/cover_letter_chunks에 다시 저장하나요?
    A. 아닙니다. 검색 의미가 긴 청크 전체에 희석되는 문제만 줄이기 위한 메모리상의
       임시 단위입니다. 최종 evidence와 content_hash의 소유 단위는 기존 청크입니다.
    """
    sentences = [
        match.group(0).strip()
        for match in _RETRIEVAL_SENTENCE_PATTERN.finditer(content)
        if match.group(0).strip()
    ]
    return sentences or ([content.strip()] if content.strip() else [])


def load_retrieval_expectations(path: Path) -> list[RetrievalExpectation]:
    import json

    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError("Retrieval 정답 파일의 최상위 값은 배열이어야 합니다.")
    return [RetrievalExpectation.model_validate(item) for item in raw]


def _load_active_aliases_by_skill(conn: Any) -> dict[int, list[str]]:
    """Strict 검증에 사용할 활성 alias를 마스터 ID별로 읽는다."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT skill_id, alias FROM skill_aliases "
            "WHERE is_active = TRUE ORDER BY skill_id, id"
        )
        rows = cur.fetchall()
    result: dict[int, list[str]] = defaultdict(list)
    for row in rows:
        skill_id = int(row["skill_id"] if isinstance(row, dict) else row[0])
        alias = str(row["alias"] if isinstance(row, dict) else row[1])
        result[skill_id].append(alias)
    return dict(result)


def _mapped_ids_by_chunk(
    mapping: UserSkillMappingReport,
) -> dict[tuple[str, int], set[int]]:
    result: dict[tuple[str, int], set[int]] = defaultdict(set)
    for skill in mapping.skills:
        for evidence in skill.evidences:
            result[(evidence.source_kind, evidence.chunk_id)].add(skill.skill_id)
    return result


def _load_chunk(conn: Any, source_kind: str, chunk_id: int, user_id: int) -> dict:
    if source_kind == "RESUME":
        sql = (
            "SELECT rc.id, rc.source_type AS context_type, rc.chunk_content, "
            "rc.content_hash FROM resume_chunks rc "
            "JOIN resumes r ON r.id = rc.resume_id "
            "WHERE rc.id = %s AND r.user_id = %s "
            "AND rc.embedding_status = 'COMPLETED' AND rc.embedding IS NOT NULL"
        )
    elif source_kind == "COVER_LETTER":
        sql = (
            "SELECT cc.id, q.question_type AS context_type, cc.chunk_content, "
            "cc.content_hash FROM cover_letter_chunks cc "
            "JOIN cover_letter_items ci ON ci.id = cc.cover_letter_item_id "
            "JOIN cover_letters cl ON cl.id = ci.cover_letter_id "
            "JOIN cover_letter_questions q ON q.id = ci.question_id "
            "WHERE cc.id = %s AND cl.user_id = %s "
            "AND cc.embedding_status = 'COMPLETED' AND cc.embedding IS NOT NULL"
        )
    else:
        raise ValueError(f"지원하지 않는 청크 출처입니다: {source_kind}")
    with conn.cursor() as cur:
        cur.execute(sql, (chunk_id, user_id))
        row = cur.fetchone()
    if row is None:
        raise ValueError(
            f"COMPLETED 청크를 찾을 수 없습니다: source={source_kind} id={chunk_id}"
        )
    if isinstance(row, dict):
        return dict(row)
    return {
        "id": row[0],
        "context_type": row[1],
        "chunk_content": row[2],
        "content_hash": row[3],
    }


def _retrieve_category(
    conn: Any,
    *,
    source_kind: str,
    chunk_id: int,
    user_id: int,
    category: SkillCategory,
    excluded_skill_ids: set[int],
    limit: int,
) -> list[MasterRetrievalHit]:
    # Q. 왜 청크 벡터를 Python으로 읽었다가 다시 SQL에 보내지 않나요?
    # A. 이미 DB에 있는 vector를 DB 내부에서 바로 비교하면 OpenAI 재호출과 벡터
    #    직렬화를 모두 피할 수 있고, content_hash가 같은 저장 벡터를 그대로 씁니다.
    if source_kind == "RESUME":
        chunk_join = (
            "SELECT rc.embedding FROM resume_chunks rc JOIN resumes r "
            "ON r.id = rc.resume_id WHERE rc.id = %s AND r.user_id = %s "
            "AND rc.embedding_status = 'COMPLETED' AND rc.embedding IS NOT NULL"
        )
    else:
        chunk_join = (
            "SELECT cc.embedding FROM cover_letter_chunks cc "
            "JOIN cover_letter_items ci ON ci.id = cc.cover_letter_item_id "
            "JOIN cover_letters cl ON cl.id = ci.cover_letter_id "
            "WHERE cc.id = %s AND cl.user_id = %s "
            "AND cc.embedding_status = 'COMPLETED' AND cc.embedding IS NOT NULL"
        )
    with conn.cursor() as cur:
        cur.execute(
            "SELECT s.id, s.name, s.category, "
            "COALESCE(s.description, '') AS description, "
            "1 - (s.embedding <=> chunk.embedding) AS similarity "
            f"FROM skills s CROSS JOIN LATERAL ({chunk_join}) chunk "
            "WHERE s.embedding IS NOT NULL AND s.category = %s "
            "AND s.id <> ALL(%s::bigint[]) "
            "ORDER BY s.embedding <=> chunk.embedding LIMIT %s",
            (chunk_id, user_id, category.value, list(excluded_skill_ids), limit),
        )
        rows = cur.fetchall()
    return [
        MasterRetrievalHit(
            skill_id=int(row["id"] if isinstance(row, dict) else row[0]),
            name=str(row["name"] if isinstance(row, dict) else row[1]),
            category=SkillCategory(
                row["category"] if isinstance(row, dict) else row[2]
            ),
            description=str(
                row["description"] if isinstance(row, dict) else row[3]
            ),
            similarity=float(
                row["similarity"] if isinstance(row, dict) else row[4]
            ),
        )
        for row in rows
    ]


def _retrieve_category_by_vector(
    conn: Any,
    *,
    vector: list[float],
    category: SkillCategory,
    excluded_skill_ids: set[int],
    limit: int,
    matched_sentence: str,
    sentence_index: int,
) -> list[MasterRetrievalHit]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT s.id, s.name, s.category, "
            "COALESCE(s.description, '') AS description, "
            "1 - (s.embedding <=> %s::vector) AS similarity "
            "FROM skills s WHERE s.embedding IS NOT NULL AND s.category = %s "
            "AND s.id <> ALL(%s::bigint[]) "
            "ORDER BY s.embedding <=> %s::vector LIMIT %s",
            (
                vector,
                category.value,
                list(excluded_skill_ids),
                vector,
                limit,
            ),
        )
        rows = cur.fetchall()
    return [
        MasterRetrievalHit(
            skill_id=int(row["id"] if isinstance(row, dict) else row[0]),
            name=str(row["name"] if isinstance(row, dict) else row[1]),
            category=SkillCategory(
                row["category"] if isinstance(row, dict) else row[2]
            ),
            description=str(
                row["description"] if isinstance(row, dict) else row[3]
            ),
            similarity=float(
                row["similarity"] if isinstance(row, dict) else row[4]
            ),
            retrieval_source="sentence",
            matched_sentence=matched_sentence,
            sentence_index=sentence_index,
        )
        for row in rows
    ]


def _merge_retrieval_hits(
    hits: list[MasterRetrievalHit],
    *,
    limit: int,
) -> list[MasterRetrievalHit]:
    """같은 마스터는 가장 강한 chunk 또는 sentence 검색 결과만 유지한다.

    Q. 여러 문장에서 같은 스킬이 검색되면 점수를 합산하지 않나요?
    A. 문장이 많은 청크가 부당하게 유리해질 수 있으므로 합산하지 않습니다. Retrieval은
       후보 생성 단계이므로 가장 직접적인 문장의 최고 유사도만 보존하고 Pass 2가
       청크 전체 문맥에서 실제 보유 역량인지 판단합니다.
    """
    best_by_skill: dict[int, MasterRetrievalHit] = {}
    for hit in hits:
        current = best_by_skill.get(hit.skill_id)
        if current is None or hit.similarity > current.similarity:
            best_by_skill[hit.skill_id] = hit
    return sorted(
        best_by_skill.values(),
        key=lambda item: (-item.similarity, item.skill_id),
    )[:limit]


def _embed_sentence_texts(
    texts: list[str],
    *,
    embedding_client: Any | None,
    embedding_cache: dict[str, list[float]],
) -> None:
    # Q. Top 20과 Top 40을 비교할 때 문장 임베딩 API를 두 번 호출하나요?
    # A. 호출자가 공유 cache를 넘기므로 같은 실행에서는 한 번만 생성합니다. 벡터는
    #    실험 중 메모리에만 있고 DB 청크 embedding을 덮어쓰지 않습니다.
    missing = list(dict.fromkeys(text for text in texts if text not in embedding_cache))
    if not missing:
        return
    client = embedding_client or _create_openai_client()
    for start in range(0, len(missing), EMBEDDING_BATCH_SIZE):
        batch = missing[start : start + EMBEDDING_BATCH_SIZE]
        vectors = _create_embeddings(client, batch)
        embedding_cache.update(zip(batch, vectors, strict=True))


def _retrieval_metric(
    chunks: list[ChunkRetrieval],
    expectations: list[RetrievalExpectation],
) -> RetrievalMetric:
    candidates_by_hash = {
        chunk.content_hash: {(hit.name, hit.category) for hit in chunk.candidates}
        for chunk in chunks
    }
    found_by_category: dict[SkillCategory, int] = defaultdict(int)
    total_by_category: dict[SkillCategory, int] = defaultdict(int)
    for expected in expectations:
        total_by_category[expected.category] += 1
        if (expected.skill_name, expected.category) in candidates_by_hash.get(
            expected.content_hash, set()
        ):
            found_by_category[expected.category] += 1
    found = sum(found_by_category.values())
    return RetrievalMetric(
        expected_count=len(expectations),
        found_count=found,
        recall_at_k=found / len(expectations) if expectations else 0.0,
        by_category={
            category.value: found_by_category[category] / count
            for category, count in total_by_category.items()
        },
    )


def retrieve_missing_master_candidates(
    conn: Any,
    extraction: SkillExtractionReport,
    pass1_mapping: UserSkillMappingReport,
    *,
    top_k_per_category: int,
    categories: frozenset[SkillCategory] = PASS2_CATEGORIES,
    expectations: list[RetrievalExpectation] | None = None,
    retrieval_mode: str = "chunk",
    sentence_top_k: int = 5,
    embedding_client: Any | None = None,
    embedding_cache: dict[str, list[float]] | None = None,
    exclude_pass1_chunk_skills: bool = True,
) -> RetrievalReport:
    if top_k_per_category < 1:
        raise ValueError("top_k_per_category는 1 이상이어야 합니다.")
    if retrieval_mode not in {"chunk", "sentence", "hybrid"}:
        raise ValueError(
            "retrieval_mode은 chunk, sentence 또는 hybrid여야 합니다."
        )
    if sentence_top_k < 1:
        raise ValueError("sentence_top_k는 1 이상이어야 합니다.")
    mapped_by_chunk = _mapped_ids_by_chunk(pass1_mapping)
    aliases_by_skill = _load_active_aliases_by_skill(conn)
    cache = embedding_cache if embedding_cache is not None else {}
    chunks: list[ChunkRetrieval] = []
    loaded_chunks: list[tuple[Any, dict]] = []
    for extracted_chunk in extraction.chunks:
        row = _load_chunk(
            conn,
            extracted_chunk.source_kind,
            extracted_chunk.chunk_id,
            extraction.user_id,
        )
        if str(row["content_hash"]) != extracted_chunk.content_hash:
            raise ValueError("추출 후 청크가 변경되어 Retrieval을 중단합니다.")
        loaded_chunks.append((extracted_chunk, row))

    sentences_by_chunk: dict[tuple[str, int], list[str]] = {}
    if retrieval_mode in {"sentence", "hybrid"}:
        for extracted_chunk, row in loaded_chunks:
            key = (extracted_chunk.source_kind, extracted_chunk.chunk_id)
            sentences_by_chunk[key] = _split_retrieval_sentences(
                str(row["chunk_content"])
            )
        _embed_sentence_texts(
            [
                sentence
                for sentences in sentences_by_chunk.values()
                for sentence in sentences
            ],
            embedding_client=embedding_client,
            embedding_cache=cache,
        )

    for extracted_chunk, row in loaded_chunks:
        excluded = (
            mapped_by_chunk.get(
                (extracted_chunk.source_kind, extracted_chunk.chunk_id), set()
            )
            if exclude_pass1_chunk_skills
            else set()
        )
        chunk_hits = (
            [
                hit
                for category in sorted(categories, key=lambda item: item.value)
                for hit in _retrieve_category(
                    conn,
                    source_kind=extracted_chunk.source_kind,
                    chunk_id=extracted_chunk.chunk_id,
                    user_id=extraction.user_id,
                    category=category,
                    excluded_skill_ids=excluded,
                    limit=top_k_per_category,
                )
            ]
            if retrieval_mode in {"chunk", "hybrid"}
            else []
        )
        sentence_hits_by_category: dict[
            SkillCategory, list[MasterRetrievalHit]
        ] = {}
        if retrieval_mode in {"sentence", "hybrid"}:
            key = (extracted_chunk.source_kind, extracted_chunk.chunk_id)
            for category in sorted(categories, key=lambda item: item.value):
                sentence_hits = [
                    hit
                    for index, sentence in enumerate(sentences_by_chunk[key])
                    for hit in _retrieve_category_by_vector(
                        conn,
                        vector=cache[sentence],
                        category=category,
                        excluded_skill_ids=excluded,
                        limit=sentence_top_k,
                        matched_sentence=sentence,
                        sentence_index=index,
                    )
                ]
                sentence_hits_by_category[category] = _merge_retrieval_hits(
                    sentence_hits,
                    limit=top_k_per_category,
                )
        if retrieval_mode == "chunk":
            hits = chunk_hits
        elif retrieval_mode == "sentence":
            hits = [
                hit
                for category in sorted(categories, key=lambda item: item.value)
                for hit in sentence_hits_by_category[category]
            ]
        else:
            # Q. hybrid가 chunk 후보 수 + sentence 후보 수를 그대로 Pass 2에 보내나요?
            # A. 아닙니다. 방식별 후보 수 차이로 평가가 왜곡되지 않도록 동일한 최종 K를
            #    유지합니다. 같은 skill_id는 유사도가 더 높은 검색 단위가 대표합니다.
            hits = []
            for category in sorted(categories, key=lambda item: item.value):
                category_chunk_hits = [
                    hit for hit in chunk_hits if hit.category is category
                ]
                hits.extend(
                    _merge_retrieval_hits(
                        [
                            *category_chunk_hits,
                            *sentence_hits_by_category[category],
                        ],
                        limit=top_k_per_category,
                    )
                )
        chunks.append(
            ChunkRetrieval(
                source_kind=extracted_chunk.source_kind,
                chunk_id=extracted_chunk.chunk_id,
                context_type=str(row["context_type"]),
                content_hash=extracted_chunk.content_hash,
                chunk_content=str(row["chunk_content"]),
                excluded_pass1_skill_ids=sorted(excluded),
                candidates=[
                    hit.model_copy(
                        update={"aliases": aliases_by_skill.get(hit.skill_id, [])}
                    )
                    for hit in hits
                ],
            )
        )
    return RetrievalReport(
        user_id=extraction.user_id,
        retrieval_mode=retrieval_mode,
        top_k_per_category=top_k_per_category,
        categories=sorted(categories, key=lambda item: item.value),
        chunks=chunks,
        metric=(
            _retrieval_metric(chunks, expectations)
            if expectations is not None
            else None
        ),
    )


_PASS2_SYSTEM_PROMPT = """당신은 이미 1차 추출이 끝난 문서의 누락 여부만 검증합니다.
사용자가 제시한 마스터 후보 중 원문에 명시되거나 완료 행동으로 직접 입증된 것만 반환합니다.
목록에 없는 skill_id를 만들지 마세요. 관련 있어 보인다는 이유만으로 선택하지 마세요.
미래 희망·계획, 회사가 요구한 역량, 단순 관심사는 보유 역량이 아닙니다.
evidence는 반드시 원문의 연속된 문구를 그대로 복사하세요. 근거가 없으면 빈 배열을 반환하세요.
"""

_STRICT_PASS2_SYSTEM_PROMPT = """당신은 사용자 보유 스킬의 엄격한 근거 검증기입니다.
후보와 원문이 단순히 관련 있다는 이유로 승인하지 마세요. 원문이 사용자의 실제 보유를
직접 증명할 때만 반환합니다. Precision이 Recall보다 중요하며 애매하면 반드시 제외합니다.

검증 정책:
1. TECHNICAL_SKILL/CERTIFICATION은 제공된 canonical name 또는 alias가 evidence에
   직접 등장해야 합니다. 상위 개념에서 구체 제품·서비스·프레임워크를 추론하지 마세요.
2. BEHAVIORAL_TRAIT은 candidate description과 직접 부합하는 관찰 가능한 완료 행동이
   evidence에 있어야 합니다. 그 행동이 오직 해당 성향으로만 설명될 필요는 없습니다.
   다만 단순히 관련 있어 보이는 행동에서 별도의 성향을 덧붙여야만 하면 제외합니다.
3. 단순 개선, 공유, 문서화, 코드 리뷰, 기준 준수, 성과 향상만으로 그와 관련된 성향을
   승격하지 마세요. 후보 정의에 필요한 상호작용·조정·학습 후 적용·문제와 원인 및 조치
   같은 구별력 있는 행동 구조가 원문 자체에 보여야 합니다.
4. BEHAVIORAL_TRAIT의 observable_action에는 후보를 직접 증명하는 원문의 연속된
   완료 행동을 복사하세요. 행동을 직접 복사할 수 없으면 해당 후보를 반환하지 마세요.
   TECHNICAL_SKILL/CERTIFICATION은 기존 직접 명시 계약만 적용하고 observable_action은
   null로 반환해도 됩니다.
5. verification_basis는 기술/자격의 직접 명시는 DIRECT_NAME_OR_ALIAS,
   행동 특성의 직접 완료 행동은 OBSERVABLE_COMPLETED_ACTION을 사용하세요.
6. 목록에 없는 skill_id를 만들지 마세요. evidence와 행동 특성의 observable_action은
   반드시 원문의 연속된 문구를 그대로 복사하세요.

판정 예시:
- 문서를 팀원에게 공유함 → 협업 REJECT (상호 조정·공동 수행이 직접 보이지 않음)
- 역할을 다시 나누고 일정을 조율해 공동 작업을 완료함 → 협업 ACCEPT
- 품질 지표를 개선함 → 학습 민첩성 REJECT (학습과 실제 적용이 직접 보이지 않음)
- 처음 쓰는 도구의 문서를 학습한 뒤 기능에 적용함 → 학습 민첩성 ACCEPT
- 사용자 피드백으로 기능을 개선함 → 문제 해결 REJECT (문제·원인·조치가 보이지 않음)
- 오류 로그로 원인을 특정하고 예외 처리 로직을 수정함 → 문제 해결 ACCEPT
"""

_STRICT_TECHNICAL_PASS2_SYSTEM_PROMPT = """당신은 사용자 보유 기술 스킬의 엄격한
직접 명시 검증기입니다. 제공된 TECHNICAL_SKILL 후보 중 canonical name 또는 alias가
원문 evidence에 직접 등장하는 후보만 반환하세요. 상위 개념에서 구체 제품·서비스·
프레임워크를 추론하지 마세요. 관련 있어 보인다는 이유만으로 선택하지 마세요.
evidence는 반드시 원문의 연속된 문구를 그대로 복사하세요. 목록에 없는 skill_id를
만들지 말고, 직접 명시된 후보가 없으면 빈 배열을 반환하세요.
"""

_BEHAVIORAL_DIRECTNESS_SYSTEM_PROMPT = """당신은 행동 특성의 직접 행동 근거를
재검토하는 엄격한 검증기입니다. 각 후보의 name/description과 원문 evidence,
observable_action을 비교하세요. 완료 행동이 존재한다는 사실만으로 행동 특성을
승인하지 마세요. description에 직접 부합하지 않고 단순히 관련된 행동이라 별도의
성향을 덧붙여야 하면 requires_trait_inference=true, mere_related_action=true입니다.
description이 요구하는 상호작용, 조정, 학습과
적용, 문제·원인·조치 같은 구별 구조가 evidence 자체에 관찰 가능하게 나타난 경우만
DIRECT_OBSERVABLE_ACTION으로 승인하세요. 개선·공유·문서화·코드 리뷰·기준 준수·
모니터링·성과 향상은 그 자체만으로 특정 성향의 직접 증거가 아닙니다. 애매하면
반드시 거절하고 모든 입력 skill_id에 대해 판정을 하나씩 반환하세요.

행동이 다른 성향으로도 설명될 수 있다는 이유만으로 거절하지 마세요. 판단 기준은
독점적 성격 증명이 아니라 해당 행동이 description의 정의와 직접 부합하는지입니다.
description의 핵심 구성요소 중 evidence에 직접 보이지 않는 내용을
missing_definition_elements에 적으세요. 하나라도 누락되면 반드시 거절합니다.
accept=true는 requires_trait_inference=false,
directly_matches_definition=true, mere_related_action=false,
missing_definition_elements=[]인 경우에만 허용합니다.
"""

def _pass2_prompt(chunk: ChunkRetrieval, *, strict: bool = False) -> str:
    candidate_lines = "\n".join(
        f"- id={hit.skill_id} | {hit.category.value} | canonical={hit.name} | "
        f"aliases={hit.aliases if strict else []} | {hit.description}"
        for hit in chunk.candidates
    )
    return (
        f"문서 종류: {chunk.source_kind}\n"
        f"문맥 유형: {chunk.context_type}\n\n"
        f"누락 가능 마스터 후보:\n{candidate_lines}\n\n"
        f"원문:\n{chunk.chunk_content}"
    )


def _behavioral_directness_prompt(
    chunk: ChunkRetrieval,
    selections: list[StrictPass2Selection],
) -> str:
    by_id = {hit.skill_id: hit for hit in chunk.candidates}
    blocks: list[str] = []
    for selection in selections:
        hit = by_id[selection.skill_id]
        blocks.append(
            "\n".join(
                (
                    f"skill_id: {hit.skill_id}",
                    f"name: {hit.name}",
                    f"description: {hit.description}",
                    f"evidence: {selection.evidence}",
                    f"observable_action: {selection.observable_action}",
                )
            )
        )
    return "\n\n---\n\n".join(blocks)


def _normalize_direct_mention(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold().strip()
    return " ".join(normalized.split())


def _contains_direct_mention(evidence: str, hit: MasterRetrievalHit) -> bool:
    """canonical/alias가 evidence에 직접 등장하는지만 일반 규칙으로 확인한다.

    Q. AWS→EC2 같은 개별 금지 목록을 두지 않는 이유는 무엇인가요?
    A. 새로운 제품마다 예외가 늘어나는 하드코딩을 피하기 위해, 모든 기술 후보에
       동일하게 canonical 또는 curated alias 직접 명시 계약을 적용합니다.
    """
    normalized_evidence = _normalize_direct_mention(evidence)
    for term in (hit.name, *hit.aliases):
        normalized_term = _normalize_direct_mention(term)
        if not normalized_term:
            continue
        start = normalized_evidence.find(normalized_term)
        while start >= 0:
            end = start + len(normalized_term)
            # 영문 제품명의 부분 문자열 충돌(Node vs Node.js 등)만 막습니다. 한국어
            # 조사("Docker로", "협업을")는 붙어 쓰므로 Unicode isalnum 경계를
            # 적용하면 정상 직접 명시를 놓치게 됩니다.
            left_ok = (
                start == 0
                or not normalized_term[0].isascii()
                or not normalized_term[0].isalnum()
                or not (
                    normalized_evidence[start - 1].isascii()
                    and normalized_evidence[start - 1].isalnum()
                )
            )
            right_ok = (
                end == len(normalized_evidence)
                or not normalized_term[-1].isascii()
                or not normalized_term[-1].isalnum()
                or not (
                    normalized_evidence[end].isascii()
                    and normalized_evidence[end].isalnum()
                )
            )
            if left_ok and right_ok:
                return True
            start = normalized_evidence.find(normalized_term, start + 1)
    return False


_BEHAVIORAL_GENERIC_TOKENS = frozenset(
    {
        "업무",
        "역량",
        "태도",
        "결과",
        "기준",
        "개선",
        "적용",
        "수행",
        "행동",
        "상황",
        "관련",
        "정해진",
        "통해",
        "위해",
        "방식",
        "내용",
        "기능",
        "사용",
    }
)
_KOREAN_TOKEN_PATTERN = re.compile(r"[0-9A-Za-z\uac00-\ud7a3]+")
_KOREAN_SUFFIXES = (
    "했습니다",
    "합니다",
    "됩니다",
    "입니다",
    "하도록",
    "하여",
    "하고",
    "하게",
    "하는",
    "하며",
    "에서",
    "에게",
    "으로",
    "와",
    "과",
    "을",
    "를",
    "이",
    "가",
    "은",
    "는",
)


def _behavioral_token_stem(token: str) -> str:
    normalized = token.casefold()
    for suffix in _KOREAN_SUFFIXES:
        if normalized.endswith(suffix) and len(normalized) - len(suffix) >= 2:
            return normalized[: -len(suffix)]
    # 명사형과 부사형의 공통 어간: 꼼꼼함 ↔ 꼼꼼하게
    if normalized.endswith("함") and len(normalized) > 2:
        return normalized[:-1]
    return normalized


def _behavioral_content_tokens(value: str) -> set[str]:
    return {
        stem
        for raw in _KOREAN_TOKEN_PATTERN.findall(
            unicodedata.normalize("NFKC", value)
        )
        if len(raw) >= 2
        for stem in [_behavioral_token_stem(raw)]
        if len(stem) >= 2 and stem not in _BEHAVIORAL_GENERIC_TOKENS
    }


def _is_behavioral_evidence_only_generic(evidence: str) -> bool:
    """내용어가 없는 지나치게 일반적인 행동 근거만 초기에 거른다.

    Q. 이 함수가 행동 특성의 최종 ACCEPT/REJECT를 결정하나요?
    A. 아닙니다. 일반 단어만 있는 빈약한 근거를 빠르게 거르는 보조 장치입니다.
       어떤 완료 행동이 특정 행동 특성을 직접 입증하는지는 마스터 description을
       함께 받은 Strict Pass 2의 공통 검증 계약이 판단합니다.
    """
    return not _behavioral_content_tokens(evidence)


def _observable_action_belongs_to_evidence(
    observable_action: str,
    evidence: str,
) -> bool:
    """LLM이 제시한 완료 행동이 승인 evidence와 같은 원문 범위인지 확인한다."""
    action = _normalize_grounding_text(observable_action)
    grounded_evidence = _normalize_grounding_text(evidence)
    return bool(action and grounded_evidence) and (
        action in grounded_evidence or grounded_evidence in action
    )


def _pass1_contract(
    mapping: UserSkillMappingReport | None,
) -> tuple[set[int], set[tuple[int, str, int, str]]]:
    if mapping is None:
        return set(), set()
    skill_ids: set[int] = set()
    evidence_keys: set[tuple[int, str, int, str]] = set()
    for skill in mapping.skills:
        skill_ids.add(skill.skill_id)
        for evidence in skill.evidences:
            evidence_keys.add(
                (
                    skill.skill_id,
                    evidence.source_kind,
                    evidence.chunk_id,
                    _normalize_direct_mention(evidence.evidence),
                )
            )
    return skill_ids, evidence_keys


_STRICT_INTENT_PATTERN = re.compile(
    r"(?:되고|하고|해보고|기여하고|성장하고)\s*싶|"
    r"(?:하겠|하겠습니다|할\s*계획|할\s*예정|목표로\s*하)"
)


def _is_strict_intent_evidence(evidence: str) -> bool:
    """수행 근거가 아니라 희망·계획 자체를 말하는 evidence를 차단한다."""
    return bool(_STRICT_INTENT_PATTERN.search(evidence))


def verify_retrieval_with_pass2(
    retrieval: RetrievalReport,
    *,
    client: Any | None = None,
    strict: bool = False,
    pass1_mapping: UserSkillMappingReport | None = None,
) -> Pass2PreviewReport:
    api_client = client or create_skill_extraction_client()
    pass1_skill_ids, pass1_evidence_keys = _pass1_contract(pass1_mapping)
    chunk_results: list[ChunkPass2Result] = []
    for chunk in retrieval.chunks:
        if not chunk.candidates:
            chunk_results.append(
                ChunkPass2Result(
                    source_kind=chunk.source_kind,
                    chunk_id=chunk.chunk_id,
                    content_hash=chunk.content_hash,
                    proposed_count=0,
                )
            )
            continue
        behavioral_directness_ids: list[int] = []
        if strict:
            # Q. 왜 기술과 행동 특성을 서로 다른 검증 호출로 나누나요?
            # A. 행동 특성의 엄격한 의미 계약이 이름 직접 명시만 보면 되는 기술
            #    후보까지 과도하게 억제하지 않도록 평가 경계를 분리하기 위해서입니다.
            strict_selections: list[StrictPass2Selection] = []
            technical_chunk = chunk.model_copy(
                update={
                    "candidates": [
                        hit
                        for hit in chunk.candidates
                        if hit.category
                        in {
                            SkillCategory.TECHNICAL_SKILL,
                            SkillCategory.CERTIFICATION,
                        }
                    ]
                }
            )
            if technical_chunk.candidates:
                technical_response = api_client.responses.parse(
                    model=SKILL_EXTRACTION_MODEL,
                    input=[
                        {
                            "role": "system",
                            "content": _STRICT_TECHNICAL_PASS2_SYSTEM_PROMPT,
                        },
                        {
                            "role": "user",
                            "content": _pass2_prompt(
                                technical_chunk,
                                strict=True,
                            ),
                        },
                    ],
                    text_format=Pass2Response,
                )
                technical_parsed = technical_response.output_parsed
                if not isinstance(technical_parsed, Pass2Response):
                    technical_parsed = Pass2Response.model_validate(
                        technical_parsed
                    )
                strict_selections.extend(
                    StrictPass2Selection(
                        skill_id=item.skill_id,
                        evidence=item.evidence,
                        observable_action=None,
                        verification_basis="DIRECT_NAME_OR_ALIAS",
                        level=item.level,
                    )
                    for item in technical_parsed.verified
                )

            behavioral_chunk = chunk.model_copy(
                update={
                    "candidates": [
                        hit
                        for hit in chunk.candidates
                        if hit.category is SkillCategory.BEHAVIORAL_TRAIT
                    ]
                }
            )
            if behavioral_chunk.candidates:
                behavioral_response = api_client.responses.parse(
                    model=SKILL_EXTRACTION_MODEL,
                    input=[
                        {"role": "system", "content": _STRICT_PASS2_SYSTEM_PROMPT},
                        {
                            "role": "user",
                            "content": _pass2_prompt(
                                behavioral_chunk,
                                strict=True,
                            ),
                        },
                    ],
                    text_format=StrictPass2Response,
                )
                behavioral_parsed = behavioral_response.output_parsed
                if not isinstance(behavioral_parsed, StrictPass2Response):
                    behavioral_parsed = StrictPass2Response.model_validate(
                        behavioral_parsed
                    )
                behavior_selections = behavioral_parsed.verified
                if behavior_selections:
                    directness_response = api_client.responses.parse(
                        model=SKILL_EXTRACTION_MODEL,
                        input=[
                            {
                                "role": "system",
                                "content": _BEHAVIORAL_DIRECTNESS_SYSTEM_PROMPT,
                            },
                            {
                                "role": "user",
                                "content": _behavioral_directness_prompt(
                                    behavioral_chunk,
                                    behavior_selections,
                                ),
                            },
                        ],
                        text_format=BehavioralDirectnessResponse,
                    )
                    directness_parsed = directness_response.output_parsed
                    if not isinstance(
                        directness_parsed,
                        BehavioralDirectnessResponse,
                    ):
                        directness_parsed = (
                            BehavioralDirectnessResponse.model_validate(
                                directness_parsed
                            )
                        )
                    accepted_behavior_ids = {
                        decision.skill_id
                        for decision in directness_parsed.decisions
                        if decision.accept
                        and not decision.requires_trait_inference
                        and decision.directly_matches_definition
                        and not decision.mere_related_action
                        and not decision.missing_definition_elements
                        and decision.reason == "DIRECT_OBSERVABLE_ACTION"
                    }
                    submitted_behavior_ids = {
                        selection.skill_id for selection in behavior_selections
                    }
                    behavioral_directness_ids.extend(
                        sorted(submitted_behavior_ids - accepted_behavior_ids)
                    )
                    strict_selections.extend(
                        selection
                        for selection in behavior_selections
                        if selection.skill_id in accepted_behavior_ids
                    )
            parsed: Pass2Response | StrictPass2Response = StrictPass2Response(
                verified=strict_selections
            )
        else:
            response = api_client.responses.parse(
                model=SKILL_EXTRACTION_MODEL,
                input=[
                    {"role": "system", "content": _PASS2_SYSTEM_PROMPT},
                    {"role": "user", "content": _pass2_prompt(chunk)},
                ],
                text_format=Pass2Response,
            )
            parsed = response.output_parsed
            if not isinstance(parsed, Pass2Response):
                parsed = Pass2Response.model_validate(parsed)

        by_id = {hit.skill_id: hit for hit in chunk.candidates}
        verified: list[VerifiedMissingSkill] = []
        invalid_ids: list[int] = []
        ungrounded_ids: list[int] = []
        direct_mention_ids: list[int] = []
        duplicate_ids: list[int] = []
        intent_ids: list[int] = []
        context_ids: list[int] = []
        behavioral_generic_ids: list[int] = []
        observable_action_ids: list[int] = []
        seen_ids: set[int] = set()
        for selected in parsed.verified:
            if selected.skill_id in seen_ids:
                continue
            seen_ids.add(selected.skill_id)
            hit = by_id.get(selected.skill_id)
            if hit is None:
                invalid_ids.append(selected.skill_id)
                continue
            evidence = _recover_verbatim_evidence(
                chunk.chunk_content, selected.evidence
            )
            if evidence is None or _is_future_only_evidence(evidence):
                ungrounded_ids.append(selected.skill_id)
                continue
            if strict and _is_strict_intent_evidence(evidence):
                intent_ids.append(selected.skill_id)
                continue
            if strict and hit.category is SkillCategory.BEHAVIORAL_TRAIT:
                # Q. 왜 LLM 판정 뒤에도 observable_action을 다시 검사하나요?
                # A. 의미 판정은 LLM이 맡되, 그 판단 근거가 실제 원문의 완료 행동인지
                #    확인하는 것은 결정적인 코드 계약으로 남겨 환각 승인을 막습니다.
                observable_action = _recover_verbatim_evidence(
                    chunk.chunk_content,
                    selected.observable_action or "",
                )
                if (
                    observable_action is None
                    or not _COMPLETED_ACTION_PATTERN.search(observable_action)
                    or _is_future_only_evidence(observable_action)
                    or _is_strict_intent_evidence(observable_action)
                    or not _observable_action_belongs_to_evidence(
                        observable_action,
                        evidence,
                    )
                ):
                    observable_action_ids.append(selected.skill_id)
                    continue
            if (
                strict
                and chunk.context_type == "CERTIFICATION"
                and hit.category is SkillCategory.TECHNICAL_SKILL
            ):
                # Q. 자격증명에 AWS가 있으면 AWS 실사용 스킬로 보지 않나요?
                # A. 자격 보유와 도구 사용은 다른 증거입니다. 자격증 문맥은
                #    CERTIFICATION 매핑에서 처리하고 Pass 2 기술 복구에는 쓰지 않습니다.
                context_ids.append(selected.skill_id)
                continue
            if (
                strict
                and hit.category
                in {SkillCategory.TECHNICAL_SKILL, SkillCategory.CERTIFICATION}
                and (
                    selected.verification_basis != "DIRECT_NAME_OR_ALIAS"
                    or not _contains_direct_mention(evidence, hit)
                )
            ):
                direct_mention_ids.append(selected.skill_id)
                continue
            if (
                strict
                and hit.category is SkillCategory.BEHAVIORAL_TRAIT
                and (
                    selected.verification_basis
                    != "OBSERVABLE_COMPLETED_ACTION"
                    or _is_behavioral_evidence_only_generic(evidence)
                )
            ):
                behavioral_generic_ids.append(selected.skill_id)
                continue
            evidence_key = (
                hit.skill_id,
                chunk.source_kind,
                chunk.chunk_id,
                _normalize_direct_mention(evidence),
            )
            if strict and evidence_key in pass1_evidence_keys:
                duplicate_ids.append(selected.skill_id)
                continue
            verified.append(
                VerifiedMissingSkill(
                    skill_id=hit.skill_id,
                    name=hit.name,
                    category=hit.category,
                    evidence=evidence,
                    level=selected.level,
                    retrieval_similarity=hit.similarity,
                    recovery_type=(
                        "EVIDENCE_ENRICHMENT"
                        if hit.skill_id in pass1_skill_ids
                        else "NEW_SKILL_RECOVERY"
                    )
                    if strict
                    else None,
                )
            )
        chunk_results.append(
            ChunkPass2Result(
                source_kind=chunk.source_kind,
                chunk_id=chunk.chunk_id,
                content_hash=chunk.content_hash,
                proposed_count=len(chunk.candidates),
                verified=verified,
                rejected_invalid_ids=invalid_ids,
                rejected_ungrounded_ids=ungrounded_ids,
                rejected_direct_mention_ids=direct_mention_ids,
                rejected_duplicate_ids=duplicate_ids,
                rejected_intent_ids=intent_ids,
                rejected_context_ids=context_ids,
                rejected_behavioral_generic_ids=behavioral_generic_ids,
                rejected_behavioral_directness_ids=(
                    behavioral_directness_ids
                ),
                rejected_observable_action_ids=observable_action_ids,
            )
        )
    return Pass2PreviewReport(
        model=SKILL_EXTRACTION_MODEL,
        verifier_mode="strict" if strict else "semantic",
        chunks=chunk_results,
    )

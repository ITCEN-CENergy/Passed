"""실제 사용자 스킬 매핑·병합·저장 결과의 구조화 모델."""

from __future__ import annotations

from pydantic import BaseModel, Field

from .skill_extraction_models import FailedChunkExtraction, SkillCategory
from .skill_mapping_models import MappingMethod
from .skill_mapping_worker import MappingFailureReason, SimilarityHit


class MappedEvidence(BaseModel):
    skill_id: int
    skill_name: str
    category: SkillCategory
    source_kind: str
    chunk_id: int
    context_type: str
    content_hash: str
    extracted_name: str
    evidence: str
    extracted_level: int = Field(ge=1, le=3)
    mapping_method: MappingMethod
    mapping_similarity: float | None = Field(default=None, ge=0, le=1)
    mapping_confidence: float = Field(ge=0, le=1)


class UnmappedEvidence(BaseModel):
    source_kind: str
    chunk_id: int
    context_type: str
    extracted_name: str
    category: SkillCategory
    evidence: str
    extracted_level: int = Field(ge=1, le=3)
    failure_reason: MappingFailureReason
    category_top_k: list[SimilarityHit] = Field(default_factory=list)


class AggregatedUserSkill(BaseModel):
    skill_id: int
    skill_name: str
    category: SkillCategory
    level: int = Field(ge=1, le=3)
    mapping_confidence: float = Field(ge=0, le=1)
    level_confidence: float = Field(ge=0, le=1)
    evidences: list[MappedEvidence]


class PersistStats(BaseModel):
    evidence_deleted: int = 0
    skill_upserted: int = 0
    evidence_inserted: int = 0
    skill_deleted: int = 0


class ProcessedChunkRef(BaseModel):
    source_kind: str
    chunk_id: int
    content_hash: str


class UserSkillMappingReport(BaseModel):
    user_id: int
    extraction_model: str
    processed_chunk_count: int = Field(default=0, ge=0)
    processed_chunks: list[ProcessedChunkRef] = Field(default_factory=list)
    skills: list[AggregatedUserSkill]
    unmapped: list[UnmappedEvidence] = Field(default_factory=list)
    extraction_failures: list[FailedChunkExtraction] = Field(default_factory=list)
    persisted: bool = False
    persist_stats: PersistStats | None = None

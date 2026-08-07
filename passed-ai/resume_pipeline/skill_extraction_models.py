"""스킬 후보 추출 단계의 구조화 입력·출력 모델."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class SkillCategory(str, Enum):
    TECHNICAL_SKILL = "TECHNICAL_SKILL"
    EXPERIENCE = "EXPERIENCE"
    BEHAVIORAL_TRAIT = "BEHAVIORAL_TRAIT"
    CERTIFICATION = "CERTIFICATION"


class SkillCandidate(BaseModel):
    """LLM이 원문 한 청크에서 찾은 매핑 전 스킬 후보."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    extracted_name: str = Field(
        min_length=1,
        max_length=100,
        description="evidence에서 확인되는 하나의 역량을 짧게 원자화한 매핑 전 이름",
    )
    category: SkillCategory
    level: int = Field(
        ge=1,
        le=3,
        description="원문 근거만으로 판단한 숙련도. 자격증 보유 후보는 항상 1",
    )
    evidence: str = Field(
        min_length=1,
        max_length=500,
        description="판단 근거가 된 원문 속 연속된 문구",
    )

    @field_validator("extracted_name", "evidence")
    @classmethod
    def reject_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("공백 문자열은 허용되지 않습니다.")
        return value.strip()

    @model_validator(mode="after")
    def certification_is_binary_owned_value(self) -> "SkillCandidate":
        if self.category is SkillCategory.CERTIFICATION and self.level != 1:
            raise ValueError("CERTIFICATION 후보의 level은 반드시 1이어야 합니다.")
        return self


class SkillExtractionResponse(BaseModel):
    """OpenAI Structured Outputs가 강제할 청크 한 건의 응답 스키마."""

    model_config = ConfigDict(extra="forbid")
    skills: list[SkillCandidate] = Field(default_factory=list, max_length=12)


class ExtractedChunkSkills(BaseModel):
    """청크 추적 정보와 검증된 스킬 후보를 묶은 결과."""

    source_kind: str
    chunk_id: int
    context_type: str
    content_hash: str
    skills: list[SkillCandidate]


class FailedChunkExtraction(BaseModel):
    source_kind: str
    chunk_id: int
    error: str


class SkillExtractionReport(BaseModel):
    user_id: int
    model: str
    chunks: list[ExtractedChunkSkills]
    failures: list[FailedChunkExtraction] = Field(default_factory=list)

    @property
    def candidate_count(self) -> int:
        return sum(len(chunk.skills) for chunk in self.chunks)


@dataclass(frozen=True)
class ExtractableChunk:
    """DB에서 읽어 LLM 입력으로 전달하는 최소 청크 정보."""

    source_kind: str
    chunk_id: int
    context_type: str
    chunk_content: str
    content_hash: str

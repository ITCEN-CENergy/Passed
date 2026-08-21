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
    def fixed_level_categories_use_storage_value_one(self) -> "SkillCandidate":
        # Q. 성향과 자격증에도 왜 level 필드가 남아 있나요?
        # A. 현재 DB와 API가 1~3 정수를 필수로 요구합니다. BEHAVIORAL_TRAIT의 1은
        #    숙련도가 아니라 '직접 행동 근거가 있음', CERTIFICATION의 1은 '보유'라는
        #    호환용 값이며 추천/UI에서는 크기를 비교하지 않습니다.
        if self.category in {
            SkillCategory.BEHAVIORAL_TRAIT,
            SkillCategory.CERTIFICATION,
        }:
            self.level = 1
        return self


class ResumeSkillExtractionResponse(BaseModel):
    """이력서 청크의 보수적인 Structured Outputs 계약."""

    model_config = ConfigDict(extra="forbid")
    skills: list[SkillCandidate] = Field(default_factory=list, max_length=12)


class CoverLetterSkillExtractionResponse(BaseModel):
    """서술형 자기소개서 청크의 확장된 Structured Outputs 계약."""

    model_config = ConfigDict(extra="forbid")
    skills: list[SkillCandidate] = Field(default_factory=list, max_length=20)


# 기존 평가·테스트 코드가 이력서 기본 계약을 계속 import할 수 있게 유지합니다.
SkillExtractionResponse = ResumeSkillExtractionResponse


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

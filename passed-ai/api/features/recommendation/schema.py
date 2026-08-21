from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class RecommendationModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class SkillFact(RecommendationModel):
    skillName: str = Field(min_length=1)
    skillType: str = Field(min_length=1)
    userLevel: int | None = Field(default=None, ge=1, le=3)
    requiredLevel: int = Field(ge=1, le=3)
    matchRate: str = Field(pattern=r"^(0(\.\d+)?|1(\.0+)?)$")
    requirementSatisfied: bool


class JobPostingContext(RecommendationModel):
    positionDetail: str | None = Field(default=None, max_length=4000)
    mainDuty: str | None = Field(default=None, max_length=4000)
    qualification: str | None = Field(default=None, max_length=4000)
    preference: str | None = Field(default=None, max_length=4000)
    companyTalentProfile: str | None = Field(default=None, max_length=2000)


class RecommendationExplanationInput(RecommendationModel):
    jobPostingId: int = Field(gt=0)
    jobPostingTitle: str = Field(min_length=1)
    companyName: str = Field(min_length=1)
    posting: JobPostingContext
    matchedSkills: list[SkillFact] = Field(default_factory=list, max_length=5)
    gapSkills: list[SkillFact] = Field(default_factory=list, max_length=5)


class RecommendationExplanationRequest(RecommendationModel):
    recommendations: list[RecommendationExplanationInput] = Field(
        min_length=1,
        max_length=20,
    )

    @model_validator(mode="after")
    def validate_unique_job_posting_ids(self) -> "RecommendationExplanationRequest":
        posting_ids = [item.jobPostingId for item in self.recommendations]
        if len(posting_ids) != len(set(posting_ids)):
            raise ValueError("jobPostingId must be unique")
        return self


class RecommendationExplanationItem(RecommendationModel):
    jobPostingId: int = Field(gt=0)
    reason: str = Field(min_length=1, max_length=600)


class RecommendationExplanationResponse(RecommendationModel):
    recommendations: list[RecommendationExplanationItem]


class SkillVerificationRequest(RecommendationModel):
    userId: int = Field(gt=0)
    targetSkillIds: list[int] = Field(min_length=1, max_length=100)

    @model_validator(mode="after")
    def validate_unique_target_skill_ids(self) -> "SkillVerificationRequest":
        if len(self.targetSkillIds) != len(set(self.targetSkillIds)):
            raise ValueError("targetSkillIds must be unique")
        return self


class SkillEvidence(RecommendationModel):
    evidenceId: int = Field(gt=0)
    text: str = Field(min_length=1, max_length=4000)
    extractedLevel: int = Field(ge=1, le=3)


class SkillVerificationCandidate(RecommendationModel):
    targetSkillId: int = Field(gt=0)
    targetSkillName: str = Field(min_length=1)
    targetSkillCategory: str = Field(min_length=1)
    targetSkillDescription: str = Field(min_length=1)
    sourceSkillId: int = Field(gt=0)
    sourceSkillName: str = Field(min_length=1)
    sourceSkillCategory: str = Field(min_length=1)
    sourceSkillDescription: str = Field(min_length=1)
    similarity: float = Field(ge=0, le=1)
    evidences: list[SkillEvidence] = Field(min_length=1, max_length=3)


class SkillVerificationSelection(RecommendationModel):
    targetSkillId: int = Field(gt=0)
    sourceSkillId: int = Field(gt=0)
    evidenceId: int = Field(gt=0)
    evidenceQuote: str = Field(min_length=1, max_length=500)
    relationship: Literal["SAME_SKILL", "TARGET_DIRECTLY_SUPPORTED"]


class SkillVerificationModelResponse(RecommendationModel):
    verified: list[SkillVerificationSelection] = Field(default_factory=list)


class DirectSkillEvidence(RecommendationModel):
    sourceKind: Literal["RESUME", "COVER_LETTER"]
    chunkId: int = Field(gt=0)
    contextType: str = Field(min_length=1)
    text: str = Field(min_length=1, max_length=4000)
    similarity: float = Field(ge=0, le=1)


class DirectSkillVerificationCandidate(RecommendationModel):
    targetSkillId: int = Field(gt=0)
    targetSkillName: str = Field(min_length=1)
    targetSkillCategory: str = Field(min_length=1)
    targetSkillDescription: str = Field(min_length=1)
    evidences: list[DirectSkillEvidence] = Field(min_length=1, max_length=2)


class DirectSkillVerificationSelection(RecommendationModel):
    targetSkillId: int = Field(gt=0)
    sourceKind: Literal["RESUME", "COVER_LETTER"]
    chunkId: int = Field(gt=0)
    evidenceQuote: str = Field(min_length=1, max_length=500)
    inferredLevel: int = Field(ge=1, le=3)


class DirectSkillVerificationModelResponse(RecommendationModel):
    verified: list[DirectSkillVerificationSelection] = Field(default_factory=list)


class VerifiedSkillMatch(RecommendationModel):
    targetSkillId: int = Field(gt=0)
    targetSkillName: str = Field(min_length=1)
    sourceSkillId: int | None = Field(default=None, gt=0)
    sourceSkillName: str | None = Field(default=None, min_length=1)
    inferredLevel: int = Field(ge=1, le=3)
    evidence: str = Field(min_length=1, max_length=500)
    similarity: float = Field(ge=0, le=1)
    relationship: Literal[
        "SAME_SKILL",
        "TARGET_DIRECTLY_SUPPORTED",
        "DIRECT_DOCUMENT_EVIDENCE",
    ]


class SkillVerificationResponse(RecommendationModel):
    verifiedSkills: list[VerifiedSkillMatch] = Field(default_factory=list)

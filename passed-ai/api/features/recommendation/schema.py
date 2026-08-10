from pydantic import BaseModel, ConfigDict, Field, model_validator


class RecommendationModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class SkillFact(RecommendationModel):
    skillName: str = Field(min_length=1)
    skillType: str = Field(min_length=1)
    evaluationType: str = Field(min_length=1)
    userLevel: int | None = Field(default=None, ge=1, le=3)
    requiredLevel: int = Field(ge=1, le=3)
    matchRate: str = Field(pattern=r"^(0(\.\d+)?|1(\.0+)?)$")
    userImportant: bool
    requirementSatisfied: bool


class RecommendationExplanationInput(RecommendationModel):
    jobPostingId: int = Field(gt=0)
    jobPostingTitle: str = Field(min_length=1)
    companyName: str = Field(min_length=1)
    rankOrder: int = Field(gt=0)
    recommendationGrade: str = Field(min_length=1)
    candidateTier: str = Field(min_length=1)
    totalScore: str = Field(min_length=1)
    requiredScore: str = Field(min_length=1)
    preferredScore: str = Field(min_length=1)
    relatedScore: str = Field(min_length=1)
    importantSkillBonus: str = Field(min_length=1)
    requiredCoverageRate: str = Field(min_length=1)
    requiredLevelMatchRate: str = Field(min_length=1)
    importantMatchCount: int = Field(ge=0)
    strengths: list[SkillFact] = Field(default_factory=list, max_length=10)
    gaps: list[SkillFact] = Field(default_factory=list, max_length=10)


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
    reason: str = Field(min_length=1)
    strengths: str = Field(min_length=1)
    weaknesses: str = Field(min_length=1)


class RecommendationExplanationResponse(RecommendationModel):
    recommendations: list[RecommendationExplanationItem]

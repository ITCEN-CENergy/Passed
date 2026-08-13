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

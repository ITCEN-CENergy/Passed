from pydantic import Field

from api.features.user_skill.schema import ApiModel


class LearningCompetencyRequest(ApiModel):
    user_id: int = Field(gt=0)
    job_posting_id: int = Field(gt=0)


class LearningCompetencyItem(ApiModel):
    standard_competency_id: int = Field(gt=0)
    standard_competency_name: str = Field(min_length=1)
    category: str
    requirement_type: str
    current_level: int = Field(ge=0, le=3)
    target_level: int = Field(ge=1, le=3)
    current_level_evidence: str | None = None


class LearningCompetencyResponse(ApiModel):
    user_id: int
    job_posting_id: int
    competencies: list[LearningCompetencyItem]

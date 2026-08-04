from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, model_validator


class CompetencyCategory(StrEnum):
    TECHNICAL_SKILL = "TECHNICAL_SKILL"
    EXPERIENCE = "EXPERIENCE"
    BEHAVIORAL_TRAIT = "BEHAVIORAL_TRAIT"
    CERTIFICATION = "CERTIFICATION"


class RequirementType(StrEnum):
    REQUIRED = "REQUIRED"
    PREFERRED = "PREFERRED"
    RELATED = "RELATED"


class MilestoneType(StrEnum):
    CONCEPT = "CONCEPT"
    PRACTICE = "PRACTICE"
    PROJECT = "PROJECT"
    ASSESSMENT = "ASSESSMENT"
    CERTIFICATION = "CERTIFICATION"


class Difficulty(StrEnum):
    BEGINNER = "BEGINNER"
    INTERMEDIATE = "INTERMEDIATE"
    ADVANCED = "ADVANCED"


class RoadmapModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class CompetencySource(RoadmapModel):
    jobPostingId: int = Field(gt=0)
    currentEvidence: str | None = Field(default=None, min_length=1)


class Competency(RoadmapModel):
    roadmapSkillKey: str = Field(min_length=1)
    standardCompetencyId: int = Field(gt=0)
    standardCompetencyName: str = Field(min_length=1)
    category: CompetencyCategory
    currentLevel: int = Field(ge=0, le=5)
    targetLevel: int = Field(ge=0, le=5)
    requirementType: RequirementType
    gapLevel: int = Field(ge=0)
    frequency: int = Field(ge=0)
    priority: int = Field(ge=0)
    sources: list[CompetencySource] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_levels(self) -> "Competency":
        if self.category == CompetencyCategory.CERTIFICATION:
            if self.currentLevel not in (0, 1) or self.targetLevel != 1:
                raise ValueError("CERTIFICATION levels must be 0 or 1 -> 1")
        elif self.currentLevel >= self.targetLevel:
            raise ValueError("currentLevel must be less than targetLevel")
        return self


class RoadmapGenerateRequest(RoadmapModel):
    userId: int = Field(gt=0)
    competencies: list[Competency] = Field(min_length=1)


class Milestone(RoadmapModel):
    title: str
    description: str
    learningObjective: str
    completionCriteria: str
    startLevel: int
    targetLevel: int
    milestoneType: MilestoneType
    difficulty: Difficulty
    estimatedMinutes: int = Field(gt=0)
    learningOrder: int = Field(gt=0)


class RoadmapSkill(RoadmapModel):
    roadmapSkillKey: str
    milestones: list[Milestone]


class RoadmapGenerateResponse(RoadmapModel):
    title: str
    skills: list[RoadmapSkill]

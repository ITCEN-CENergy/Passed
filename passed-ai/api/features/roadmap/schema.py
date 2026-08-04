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
    currentLevel: int = Field(ge=0, le=3)
    targetLevel: int = Field(ge=1, le=3)
    requirementType: RequirementType
    gapLevel: int = Field(ge=0)
    frequency: int = Field(ge=0)
    priority: int = Field(ge=0)
    sources: list[CompetencySource] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_levels(self) -> "Competency":
        if self.category == CompetencyCategory.CERTIFICATION:
            if self.currentLevel != 0 or self.targetLevel != 1:
                raise ValueError("a certification gap must be 0 -> 1")
        elif not 1 <= self.currentLevel < self.targetLevel <= 3:
            raise ValueError(
                "non-certification levels must satisfy "
                "1 <= currentLevel < targetLevel <= 3"
            )
        if self.gapLevel != self.targetLevel - self.currentLevel:
            raise ValueError("gapLevel must equal targetLevel - currentLevel")
        return self


class RoadmapGenerateRequest(RoadmapModel):
    userId: int = Field(gt=0)
    competencies: list[Competency] = Field(min_length=1, max_length=10)

    @model_validator(mode="after")
    def validate_unique_keys(self) -> "RoadmapGenerateRequest":
        keys = [item.roadmapSkillKey for item in self.competencies]
        if len(keys) != len(set(keys)):
            raise ValueError("roadmapSkillKey must be unique")
        return self


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


class MilestoneSlot(RoadmapModel):
    startLevel: int
    targetLevel: int
    learningOrder: int = Field(gt=0)


class GeneratedMilestoneContent(RoadmapModel):
    title: str = Field(min_length=1, max_length=100)
    description: str = Field(min_length=1, max_length=500)
    learningObjective: str = Field(min_length=1, max_length=300)
    completionCriteria: str = Field(min_length=1, max_length=300)
    milestoneType: MilestoneType
    difficulty: Difficulty
    estimatedMinutes: int = Field(ge=30, le=2400)


class GeneratedSkillContent(RoadmapModel):
    roadmapSkillKey: str = Field(min_length=1)
    milestones: list[GeneratedMilestoneContent] = Field(min_length=1, max_length=2)


class GeneratedRoadmapContent(RoadmapModel):
    title: str = Field(min_length=1, max_length=100)
    skills: list[GeneratedSkillContent] = Field(min_length=1, max_length=10)

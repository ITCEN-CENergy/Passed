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


class LearningResourceType(StrEnum):
    KMOOC_COURSE = "KMOOC_COURSE"
    BOOK = "BOOK"
    WEB_RESOURCE = "WEB_RESOURCE"


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
            if self.currentLevel not in (0, 1) or self.targetLevel != 1:
                raise ValueError("a certification competency must be 0 -> 1 or 1 -> 1")
        elif not 1 <= self.currentLevel <= self.targetLevel <= 3:
            raise ValueError(
                "non-certification levels must satisfy "
                "1 <= currentLevel <= targetLevel <= 3"
            )
        if self.gapLevel != max(self.targetLevel - self.currentLevel, 0):
            raise ValueError("gapLevel must equal max(targetLevel - currentLevel, 0)")
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
    learningResources: list["LearningResource"] = Field(default_factory=list)


class LearningResource(RoadmapModel):
    resourceId: str = Field(min_length=1)
    resourceType: LearningResourceType
    title: str = Field(min_length=1)
    description: str = ""
    provider: str = Field(min_length=1)
    url: str = Field(min_length=1)
    thumbnailUrl: str | None = None
    authors: list[str] = Field(default_factory=list)
    isOfficial: bool = False
    isFree: bool | None = None


class RoadmapSkill(RoadmapModel):
    roadmapSkillKey: str
    milestones: list[Milestone]


class RoadmapGenerateResponse(RoadmapModel):
    title: str
    skills: list[RoadmapSkill]


class LearningStage(RoadmapModel):
    startLevel: int
    targetLevel: int


class GeneratedResourceRecommendation(RoadmapModel):
    resourceId: str = Field(min_length=1)
    recommendationReason: str = Field(min_length=1, max_length=300)


class GeneratedMilestoneContent(RoadmapModel):
    title: str = Field(min_length=1, max_length=100)
    description: str = Field(min_length=1, max_length=500)
    learningObjective: str = Field(min_length=1, max_length=300)
    completionCriteria: str = Field(min_length=1, max_length=300)
    milestoneType: MilestoneType
    difficulty: Difficulty
    estimatedMinutes: int = Field(ge=30, le=2400)
    resourceRecommendations: list[GeneratedResourceRecommendation] = Field(max_length=3)


class GeneratedLearningStage(RoadmapModel):
    startLevel: int
    targetLevel: int
    milestones: list[GeneratedMilestoneContent] = Field(min_length=3, max_length=4)


class ModelGeneratedSkillContent(RoadmapModel):
    """Content authored by the model; application correlation keys are excluded."""
    stages: list[GeneratedLearningStage] = Field(min_length=1, max_length=2)


class ModelGeneratedRoadmapContent(RoadmapModel):
    title: str = Field(min_length=1, max_length=100)
    skills: list[ModelGeneratedSkillContent] = Field(min_length=1, max_length=1)


class GeneratedSkillContent(RoadmapModel):
    roadmapSkillKey: str = Field(min_length=1)
    stages: list[GeneratedLearningStage] = Field(min_length=1, max_length=2)


class GeneratedRoadmapContent(RoadmapModel):
    title: str = Field(min_length=1, max_length=100)
    skills: list[GeneratedSkillContent] = Field(min_length=1, max_length=10)

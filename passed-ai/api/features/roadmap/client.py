from openai import AsyncOpenAI

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.exceptions import (
    RoadmapConfigurationError,
    RoadmapGenerationError,
)
from api.features.roadmap.prompt import SYSTEM_PROMPT, build_user_prompt
from api.features.roadmap.schema import (
    Competency,
    ModelGeneratedRoadmapContent,
    LearningResource,
    LearningStage,
    ModelGeneratedSingleStageRoadmapContent,
    ModelGeneratedTwoStageRoadmapContent,
)


class OpenAiRoadmapClient:
    def __init__(self, settings: RoadmapSettings) -> None:
        if not settings.openai_api_key:
            raise RoadmapConfigurationError(
                "OPENAI_API_KEY is required when ROADMAP_GENERATOR=llm"
            )
        self._model = settings.model
        self._client = AsyncOpenAI(
            api_key=settings.openai_api_key,
            timeout=settings.timeout_seconds,
            max_retries=settings.max_retries,
        )

    async def generate(
        self,
        competencies: list[Competency],
        stages_by_key: dict[str, list[LearningStage]],
        resources_by_key: dict[str, list[LearningResource]],
    ) -> ModelGeneratedRoadmapContent:
        stage_count = sum(len(stages) for stages in stages_by_key.values())
        if stage_count == 1:
            response_format = ModelGeneratedSingleStageRoadmapContent
        elif stage_count == 2:
            response_format = ModelGeneratedTwoStageRoadmapContent
        else:
            raise RoadmapGenerationError(
                f"roadmap generation requires one or two stages, got {stage_count}"
            )
        response = await self._client.responses.parse(
            model=self._model,
            input=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": build_user_prompt(
                    competencies, stages_by_key, resources_by_key
                )},
            ],
            text_format=response_format,
        )
        if response.output_parsed is None:
            raise RoadmapGenerationError("roadmap model returned no parsed output")
        return ModelGeneratedRoadmapContent.model_validate(
            response.output_parsed.model_dump()
        )

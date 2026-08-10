from openai import AsyncOpenAI

from api.features.recommendation.config import RecommendationSettings
from api.features.recommendation.exceptions import (
    RecommendationConfigurationError,
    RecommendationExplanationGenerationError,
)
from api.features.recommendation.prompt import SYSTEM_PROMPT
from api.features.recommendation.schema import (
    RecommendationExplanationRequest,
    RecommendationExplanationResponse,
)


class OpenAiRecommendationExplanationClient:
    def __init__(self, settings: RecommendationSettings) -> None:
        if not settings.openai_api_key:
            raise RecommendationConfigurationError("OPENAI_API_KEY is required")
        self._api_key = settings.openai_api_key
        self._model = settings.model
        self._timeout_seconds = settings.timeout_seconds
        self._max_retries = settings.max_retries

    async def generate(
        self,
        request: RecommendationExplanationRequest,
    ) -> RecommendationExplanationResponse:
        async with AsyncOpenAI(
            api_key=self._api_key,
            timeout=self._timeout_seconds,
            max_retries=self._max_retries,
        ) as client:
            response = await client.responses.parse(
                model=self._model,
                input=[
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": request.model_dump_json()},
                ],
                text_format=RecommendationExplanationResponse,
            )
        if response.output_parsed is None:
            raise RecommendationExplanationGenerationError(
                "recommendation explanation model returned no parsed output"
            )
        return response.output_parsed

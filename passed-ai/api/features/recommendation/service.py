from collections.abc import Awaitable
from typing import Protocol

from api.features.recommendation.client import OpenAiRecommendationExplanationClient
from api.features.recommendation.config import get_recommendation_settings
from api.features.recommendation.exceptions import RecommendationExplanationGenerationError
from api.features.recommendation.schema import (
    RecommendationExplanationRequest,
    RecommendationExplanationResponse,
)


class RecommendationExplanationGenerator(Protocol):
    def generate(
        self,
        request: RecommendationExplanationRequest,
    ) -> Awaitable[RecommendationExplanationResponse]: ...


async def generate_recommendation_explanations(
    request: RecommendationExplanationRequest,
    generator: RecommendationExplanationGenerator | None = None,
) -> RecommendationExplanationResponse:
    selected_generator = generator or OpenAiRecommendationExplanationClient(
        get_recommendation_settings()
    )
    response = await selected_generator.generate(request)
    _validate_response(request, response)
    return response


def _validate_response(
    request: RecommendationExplanationRequest,
    response: RecommendationExplanationResponse,
) -> None:
    expected_ids = {item.jobPostingId for item in request.recommendations}
    actual_ids = [item.jobPostingId for item in response.recommendations]
    if len(actual_ids) != len(set(actual_ids)):
        raise RecommendationExplanationGenerationError(
            "recommendation explanation contains a duplicated jobPostingId"
        )
    if set(actual_ids) != expected_ids:
        raise RecommendationExplanationGenerationError(
            "recommendation explanation does not match requested jobPostingIds"
        )

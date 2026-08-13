from fastapi import APIRouter, HTTPException
from openai import (
    APIConnectionError,
    APITimeoutError,
    BadRequestError,
    InternalServerError,
    RateLimitError,
)

from api.features.recommendation.exceptions import (
    RecommendationConfigurationError,
    RecommendationExplanationGenerationError,
)
from api.features.recommendation.schema import (
    RecommendationExplanationRequest,
    RecommendationExplanationResponse,
)
from api.features.recommendation.service import generate_recommendation_explanations


router = APIRouter(prefix="/api/v1/recommendations", tags=["recommendations"])


@router.post("/explanations", response_model=RecommendationExplanationResponse)
async def generate_explanations(
    request: RecommendationExplanationRequest,
) -> RecommendationExplanationResponse:
    try:
        return await generate_recommendation_explanations(request)
    except APITimeoutError as exception:
        raise HTTPException(
            status_code=504,
            detail="recommendation explanation model timed out",
        ) from exception
    except (APIConnectionError, InternalServerError, RateLimitError) as exception:
        raise HTTPException(
            status_code=503,
            detail="recommendation explanation model is unavailable",
        ) from exception
    except RecommendationConfigurationError as exception:
        raise HTTPException(status_code=503, detail=str(exception)) from exception
    except (
        BadRequestError,
        RecommendationExplanationGenerationError,
        ValueError,
    ) as exception:
        raise HTTPException(
            status_code=502,
            detail="recommendation explanation model returned invalid output",
        ) from exception

from fastapi import APIRouter, HTTPException
from openai import (
    APIConnectionError,
    APITimeoutError,
    BadRequestError,
    InternalServerError,
    RateLimitError,
)

from api.features.roadmap.exceptions import (
    RoadmapConfigurationError,
    RoadmapGenerationError,
)
from api.features.roadmap.schema import RoadmapGenerateRequest, RoadmapGenerateResponse
from api.features.roadmap.service import generate_roadmap


router = APIRouter(prefix="/api/v1/roadmaps", tags=["roadmaps"])


@router.post("/generate", response_model=RoadmapGenerateResponse)
def generate(request: RoadmapGenerateRequest) -> RoadmapGenerateResponse:
    try:
        return generate_roadmap(request)
    except APITimeoutError as exception:
        raise HTTPException(status_code=504, detail="roadmap model timed out") from exception
    except (APIConnectionError, InternalServerError, RateLimitError) as exception:
        raise HTTPException(status_code=503, detail="roadmap model is unavailable") from exception
    except RoadmapConfigurationError as exception:
        raise HTTPException(status_code=503, detail=str(exception)) from exception
    except (BadRequestError, RoadmapGenerationError, ValueError) as exception:
        raise HTTPException(status_code=502, detail="roadmap model returned invalid output") from exception

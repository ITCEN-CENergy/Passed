from collections.abc import AsyncIterator
from typing import Annotated

import httpx
from fastapi import APIRouter, Depends, HTTPException, Request
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


async def get_http_client(request: Request) -> AsyncIterator[httpx.AsyncClient]:
    shared_client = getattr(request.app.state, "http_client", None)
    if shared_client is not None:
        yield shared_client
        return
    # TestClient can be used without a lifespan context. Keep that usage safe by
    # creating a request-scoped fallback client.
    async with httpx.AsyncClient() as client:
        yield client


@router.post("/generate", response_model=RoadmapGenerateResponse)
async def generate(
    request: RoadmapGenerateRequest,
    http_client: Annotated[httpx.AsyncClient, Depends(get_http_client)],
) -> RoadmapGenerateResponse:
    try:
        return await generate_roadmap(request, http_client=http_client)
    except TimeoutError as exception:
        raise HTTPException(
            status_code=504, detail="roadmap generation timed out"
        ) from exception
    except APITimeoutError as exception:
        raise HTTPException(status_code=504, detail="roadmap model timed out") from exception
    except (APIConnectionError, InternalServerError, RateLimitError) as exception:
        raise HTTPException(status_code=503, detail="roadmap model is unavailable") from exception
    except RoadmapConfigurationError as exception:
        raise HTTPException(status_code=503, detail=str(exception)) from exception
    except (BadRequestError, RoadmapGenerationError, ValueError) as exception:
        raise HTTPException(status_code=502, detail="roadmap model returned invalid output") from exception

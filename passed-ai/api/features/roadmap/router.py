from fastapi import APIRouter

from api.features.roadmap.schema import RoadmapGenerateRequest, RoadmapGenerateResponse
from api.features.roadmap.service import generate_roadmap


router = APIRouter(prefix="/api/v1/roadmaps", tags=["roadmaps"])


@router.post("/generate", response_model=RoadmapGenerateResponse)
async def generate(request: RoadmapGenerateRequest) -> RoadmapGenerateResponse:
    return generate_roadmap(request)

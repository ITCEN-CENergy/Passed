import json

from openai import AsyncOpenAI

from api.features.roadmap.config import get_roadmap_settings
from api.features.roadmap.exceptions import RoadmapConfigurationError, RoadmapGenerationError
from api.features.roadmap.schema import (
    ReplanAction,
    ReplanDecision,
    ReplanMilestoneStatus,
    RoadmapReplanRequest,
    RoadmapReplanResponse,
)


SYSTEM_PROMPT = """You replan learning roadmaps for Korean job seekers.
Return all user-facing text in Korean. Return exactly one decision for every input milestone.
COMPLETED and IN_PROGRESS milestones must be KEEP. Optional milestones should be KEEP.
Only NOT_STARTED required milestones may be REMOVE. Keep at least one milestone per roadmapSkillId.
Prefer removing duplicated introductory content and lower-value theory while preserving projects,
practice, assessment, and prerequisite progression. Aim for a modest, defensible reduction rather
than deleting most of the roadmap. KEEP decisions must have a positive learningOrder and REMOVE
decisions must have null learningOrder. Never invent milestone IDs.
"""


def _fake_replan(request: RoadmapReplanRequest) -> RoadmapReplanResponse:
    decisions = [
        ReplanDecision(
            milestoneId=item.milestoneId,
            action=ReplanAction.KEEP,
            learningOrder=item.learningOrder,
            reason="현재 학습 계획과 진행 상태를 안전하게 유지합니다.",
        )
        for item in request.milestones
    ]
    return RoadmapReplanResponse(
        summary="완료 및 진행 상태를 보존하여 현재 로드맵을 유지했습니다.",
        decisions=decisions,
    )


async def replan_roadmap(request: RoadmapReplanRequest) -> RoadmapReplanResponse:
    settings = get_roadmap_settings()
    if settings.generator == "fake":
        return _fake_replan(request)
    if not settings.openai_api_key:
        raise RoadmapConfigurationError(
            "OPENAI_API_KEY is required when ROADMAP_GENERATOR=llm"
        )
    client = AsyncOpenAI(
        api_key=settings.openai_api_key,
        timeout=settings.timeout_seconds,
        max_retries=settings.max_retries,
    )
    response = await client.responses.parse(
        model=settings.model,
        input=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": json.dumps(
                request.model_dump(mode="json"), ensure_ascii=False, separators=(",", ":")
            )},
        ],
        text_format=RoadmapReplanResponse,
    )
    if response.output_parsed is None:
        raise RoadmapGenerationError("roadmap replan model returned no parsed output")
    return response.output_parsed

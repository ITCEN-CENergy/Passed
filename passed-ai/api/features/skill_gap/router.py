from fastapi import APIRouter, HTTPException

from .schema import LearningCompetencyRequest, LearningCompetencyResponse
from .service import SkillGapResourceNotFoundError, analyze_learning_competencies


router = APIRouter(prefix="/api/v1/skill-gaps", tags=["skill-gaps"])


@router.post("/learning-competencies", response_model=LearningCompetencyResponse)
def get_learning_competencies(
    request: LearningCompetencyRequest,
) -> LearningCompetencyResponse:
    try:
        return analyze_learning_competencies(request.user_id, request.job_posting_id)
    except SkillGapResourceNotFoundError as exception:
        raise HTTPException(status_code=404, detail=str(exception)) from exception

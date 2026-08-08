from fastapi import APIRouter, HTTPException

from resume_pipeline.db import EmbeddingSchemaError
from resume_pipeline.pipeline import MissingResumeError
from resume_pipeline.user_resolver import UserNotFoundError

from .schema import UserSkillExtractionRequest, UserSkillExtractionResponse
from .service import (
    UserSkillPipelineConfigurationError,
    UserSkillPipelineExecutionError,
    run_user_skill_analysis,
)


router = APIRouter(prefix="/api/v1/user-skills", tags=["user-skills"])


@router.post("/extractions", response_model=UserSkillExtractionResponse)
def extract_user_skills(
    request: UserSkillExtractionRequest,
) -> UserSkillExtractionResponse:
    try:
        return run_user_skill_analysis(request.user_id)
    except (UserNotFoundError, MissingResumeError) as exception:
        raise HTTPException(status_code=404, detail=str(exception)) from exception
    except UserSkillPipelineConfigurationError as exception:
        raise HTTPException(status_code=503, detail=str(exception)) from exception
    except (EmbeddingSchemaError, UserSkillPipelineExecutionError) as exception:
        raise HTTPException(status_code=502, detail=str(exception)) from exception
    except ValueError as exception:
        raise HTTPException(status_code=422, detail=str(exception)) from exception

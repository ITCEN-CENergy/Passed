from fastapi import APIRouter
from .schema import (
    CoverLetterEditRequest,
    CoverLetterEditResponse,
    CoverLetterSuggestionResponse,
    CoverLetterReviewRequest,
    CoverLetterReviewResponse,
)
from .service import (
    process_cover_letter_chain,
    process_cover_letter_review_chain,
    process_cover_letter_suggestion_chain,
)

router = APIRouter(
    prefix="/coverletter",
    tags=["CoverLetter"]
)

@router.post("/edit", response_model=CoverLetterEditResponse)
async def edit_cover_letter(request: CoverLetterEditRequest):
    """
    자기소개서를 입력받아 다단계(LangChain 파이프라인) 분석 및 첨삭 결과를 반환합니다.
    """
    result = process_cover_letter_chain(
        question=request.question,
        content=request.content,
        job_description=request.job_description or "",
        user_skills=[skill.model_dump() for skill in request.user_skills],
    )
    
    return CoverLetterEditResponse(
        qa_alignment_score=result["qa_alignment_score"],
        shortcomings=result["shortcomings"],
        recommended_revision_direction=result["recommended_revision_direction"],
    )


@router.post("/suggest", response_model=CoverLetterSuggestionResponse)
async def suggest_cover_letter(request: CoverLetterEditRequest):
    """사용자가 요청한 경우에만 피드백을 반영한 추천 수정안을 생성합니다."""
    result = process_cover_letter_suggestion_chain(
        question=request.question,
        content=request.content,
        job_description=request.job_description or "",
        user_skills=[skill.model_dump() for skill in request.user_skills],
        character_limit=request.character_limit,
    )
    return CoverLetterSuggestionResponse(suggested_answer=result)


@router.post("/review", response_model=CoverLetterReviewResponse)
async def review_cover_letter(request: CoverLetterReviewRequest):
    """Return only aggregate and item feedback needed by the feedback page."""
    result = process_cover_letter_review_chain(
        items=[item.model_dump() for item in request.items],
        job_description=request.job_description or "",
        user_skills=[skill.model_dump() for skill in request.user_skills],
    )
    return CoverLetterReviewResponse.model_validate(result)

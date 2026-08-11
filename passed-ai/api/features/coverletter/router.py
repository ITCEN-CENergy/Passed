from fastapi import APIRouter
from .schema import CoverLetterEditRequest, CoverLetterEditResponse
from .service import process_cover_letter_chain

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
        job_description=request.job_description or ""
    )
    
    return CoverLetterEditResponse(
        qa_alignment_score=result["qa_alignment_score"],
        qa_alignment_feedback=result["qa_alignment_feedback"],
        jd_fit_feedback=result["jd_fit_feedback"],
        final_edited_content=result["final_edited_content"]
    )

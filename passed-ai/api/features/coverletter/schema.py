from pydantic import BaseModel
from typing import Optional

class CoverLetterEditRequest(BaseModel):
    question: str
    content: str
    job_description: Optional[str] = None

class CoverLetterEditResponse(BaseModel):
    qa_alignment_score: int
    qa_alignment_feedback: str
    jd_fit_feedback: str
    final_edited_content: str

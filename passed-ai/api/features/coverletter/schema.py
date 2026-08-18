from typing import Optional

from pydantic import BaseModel, Field

class CoverLetterEditRequest(BaseModel):
    question: str
    content: str
    job_description: Optional[str] = None

class CoverLetterEditResponse(BaseModel):
    qa_alignment_score: int
    shortcomings: str
    recommended_revision_direction: str


class CoverLetterSuggestionResponse(BaseModel):
    suggested_answer: str = Field(min_length=1)


class CoverLetterReviewItemRequest(BaseModel):
    item_id: int = Field(gt=0)
    display_order: int = Field(gt=0)
    question: str = Field(min_length=1)
    content: str = Field(min_length=1)
    character_limit: Optional[int] = Field(default=None, gt=0)


class CoverLetterReviewRequest(BaseModel):
    items: list[CoverLetterReviewItemRequest] = Field(min_length=1)
    job_description: Optional[str] = None


class CoverLetterReviewItemResponse(BaseModel):
    item_id: int
    display_order: int
    qa_alignment_score: int = Field(ge=0, le=100)
    shortcomings: str
    recommended_revision_direction: str


class CoverLetterOverallFeedbackResponse(BaseModel):
    overall_score: int = Field(ge=0, le=100)
    summary: str
    strengths: str
    improvements: str


class CoverLetterReviewResponse(BaseModel):
    overall_feedback: CoverLetterOverallFeedbackResponse
    items: list[CoverLetterReviewItemResponse]

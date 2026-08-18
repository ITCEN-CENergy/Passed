import json

from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field

from core.config import get_settings

# LLM 초기화 (환경에 맞게 모델 변경 가능, 예: gpt-4o 또는 gemini-pro)
# 실제 환경에서는 의존성 주입(Dependency Injection) 등으로 관리하는 것이 좋습니다.
# 1. 질문과 응답의 일치도 및 표현 품질 확인 (Q&A Alignment)
class ItemFeedbackOutput(BaseModel):
    score: int = Field(ge=1, le=100, description="1에서 100 사이의 점수")
    shortcomings: str = Field(min_length=1, description="답변의 미흡한 부분")
    recommended_revision_direction: str = Field(
        min_length=1,
        description="미흡한 부분을 보완할 추천 수정 방향",
    )


class OverallReviewOutput(BaseModel):
    overall_score: int = Field(ge=0, le=100, description="자기소개서 전체 완성도 점수")
    summary: str = Field(min_length=1, description="전체 피드백 요약")
    strengths: str = Field(min_length=1, description="문항 전반에서 반복되는 강점")
    improvements: str = Field(min_length=1, description="우선순위가 높은 공통 개선점")


FEEDBACK_SECTION_LABELS = {
    "content_specificity": "내용 구체성",
    "logical_flow": "논리성",
    "grammar_and_expression": "문법 및 표현",
    "grammatical_expression": "문법 및 표현",
    "improvement_suggestion": "개선 방향",
}


def _feedback_to_text(value) -> str:
    """Normalize occasional nested LLM feedback into the public string contract."""
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, dict):
        sections = []
        for key, section_value in value.items():
            text = _feedback_to_text(section_value)
            if text:
                label = FEEDBACK_SECTION_LABELS.get(key, key.replace("_", " "))
                sections.append(f"{label}: {text}")
        return "\n".join(sections)
    if isinstance(value, (list, tuple)):
        return "\n".join(
            text for item in value if (text := _feedback_to_text(item))
        )
    return str(value).strip()

item_feedback_prompt = ChatPromptTemplate.from_messages([
    ("system", """당신은 엄격한 면접관이자 자기소개서 편집자입니다.
질문의 핵심 의도와 답변의 일치도를 100점 만점으로 평가하고, 채용 공고가 제공된 경우 직무 적합성도 함께 검토하세요.
미흡한 부분에는 근거가 부족하거나 논리적 연결이 약한 부분, 맞춤법·띄어쓰기·문법·모호한 표현을 구체적으로 설명하세요.
추천 수정 방향에는 각 미흡한 부분을 어떻게 보완할지 행동 가능한 방법을 제안하세요.
원문에 없는 사실, 경험, 수치를 만들지 마세요.
답변 전체를 교정한 별도 원고는 만들지 마세요.
마크다운 제목, 글머리 기호, 굵게 표시 문법을 사용하지 말고 일반 문장만 작성하세요.
반드시 score, shortcomings, recommended_revision_direction 필드를 가진 JSON 객체로 반환하세요."""),
    ("user", "질문: {question}\n\n답변: {content}\n\n채용 공고: {job_description}")
])

# 3. 피드백을 반영한 최종 첨삭 (Custom Criteria Editing)
final_edit_prompt = ChatPromptTemplate.from_messages([
    ("system", """당신은 전문 취업 컨설턴트이자 교정 편집자입니다.
주어진 분석을 바탕으로 자기소개서를 최종 첨삭하세요.
원문의 사실과 경험을 임의로 추가하지 말고, 맞춤법·띄어쓰기·문법·어색한 표현을 바로잡으면서 가독성과 설득력을 높이세요.
설명, 제목, 마크다운 문법 없이 완성된 최종본만 반환하세요."""),
    ("user", """
원본 질문: {question}
원본 내용: {content}
미흡한 부분: {shortcomings}
추천 수정 방향: {recommended_revision_direction}

위 내용을 종합하여 가장 완벽하고 설득력 있는 자기소개서 최종본을 작성해 주세요.
""")
])

overall_review_prompt = ChatPromptTemplate.from_messages([
    ("system", """당신은 채용 담당자이자 자기소개서 전문 컨설턴트입니다.
여러 문항의 원문과 문항별 분석 결과를 함께 검토하여 자기소개서 전체의 완성도를 평가하세요.
문항 하나의 표현을 반복하지 말고, 문항 간 일관성·경험의 중복·직무 적합성·근거의 구체성을 종합하세요.
원문에 없는 사실을 만들지 마세요.
각 텍스트 필드에는 마크다운 제목, 글머리 기호, 굵게 표시 문법을 사용하지 마세요.
반드시 overall_score, summary, strengths, improvements 필드를 가진 JSON 객체만 반환하세요.
overall_score는 0에서 100 사이의 정수입니다."""),
    ("user", """채용 공고(분석에만 사용):
{job_description}

문항별 원문 및 분석 결과:
{items_json}
""")
])


def _create_cover_letter_chains():
    """Create OpenAI-dependent chains only when the cover-letter API is called."""
    settings = get_settings()
    llm = ChatOpenAI(
        api_key=settings.openai_api_key,
        model="gpt-4o-mini",
        temperature=0.2,
    )
    item_feedback_llm = llm.with_structured_output(ItemFeedbackOutput, method="json_schema")
    return (
        item_feedback_prompt | item_feedback_llm,
        final_edit_prompt | llm | StrOutputParser(),
    )


def _create_overall_review_chain():
    """Create the aggregate-review chain only for the multi-item review API."""
    settings = get_settings()
    llm = ChatOpenAI(
        api_key=settings.openai_api_key,
        model="gpt-4o-mini",
        temperature=0.2,
    )
    overall_llm = llm.with_structured_output(
        OverallReviewOutput,
        method="json_schema",
    )
    return overall_review_prompt | overall_llm


def _process_cover_letter_with_chains(
    question: str,
    content: str,
    job_description: str,
    chains: tuple,
) -> dict:
    item_feedback_chain, _ = chains

    feedback_result = item_feedback_chain.invoke({
        "question": question,
        "content": content,
        "job_description": job_description or "제공된 채용 공고 정보가 없습니다.",
    })
    if isinstance(feedback_result, BaseModel):
        feedback_result = feedback_result.model_dump()

    return {
        "qa_alignment_score": feedback_result.get("score", 0),
        "shortcomings": _feedback_to_text(feedback_result.get("shortcomings")),
        "recommended_revision_direction": _feedback_to_text(
            feedback_result.get("recommended_revision_direction")
        ),
    }

def process_cover_letter_chain(question: str, content: str, job_description: str = "") -> dict:
    """Run the existing single-item cover-letter editing pipeline."""
    return _process_cover_letter_with_chains(
        question,
        content,
        job_description,
        _create_cover_letter_chains(),
    )


def process_cover_letter_suggestion_chain(
    question: str,
    content: str,
    job_description: str = "",
) -> str:
    """분석과 분리된 사용자 요청 시점에만 추천 수정안을 생성합니다."""
    chains = _create_cover_letter_chains()
    analysis = _process_cover_letter_with_chains(question, content, job_description, chains)
    return chains[1].invoke({
        "question": question,
        "content": content,
        "shortcomings": analysis["shortcomings"],
        "recommended_revision_direction": analysis["recommended_revision_direction"],
    }).strip()

# 전체 자기소개서를 읽고 리뷰하는 기능
def process_cover_letter_review_chain(items: list[dict], job_description: str = "") -> dict:
    """Generate item edits and one aggregate review without returning page metadata."""
    item_chains = _create_cover_letter_chains()
    reviewed_items = []

    for item in sorted(items, key=lambda value: value["display_order"]):
        result = _process_cover_letter_with_chains(
            item["question"],
            item["content"],
            job_description,
            item_chains,
        )
        reviewed_items.append({
            "item_id": item["item_id"],
            "display_order": item["display_order"],
            **result,
        })

    aggregate_source = [
        {
            "item_id": item["item_id"],
            "display_order": item["display_order"],
            "question": item["question"],
            "content": item["content"],
            "character_limit": item.get("character_limit"),
            "analysis": reviewed,
        }
        for item, reviewed in zip(
            sorted(items, key=lambda value: value["display_order"]),
            reviewed_items,
        )
    ]
    overall_raw = _create_overall_review_chain().invoke({
        "job_description": job_description or "제공된 채용 공고 정보가 없습니다.",
        "items_json": json.dumps(aggregate_source, ensure_ascii=False),
    })
    if isinstance(overall_raw, BaseModel):
        overall_raw = overall_raw.model_dump()
    normalized_overall = {
        **overall_raw,
        "summary": _feedback_to_text(overall_raw.get("summary")),
        "strengths": _feedback_to_text(overall_raw.get("strengths")),
        "improvements": _feedback_to_text(overall_raw.get("improvements")),
    }
    overall_feedback = OverallReviewOutput.model_validate(normalized_overall).model_dump()

    return {
        "overall_feedback": overall_feedback,
        "items": reviewed_items,
    }

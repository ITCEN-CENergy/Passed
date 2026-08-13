import json

from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser, JsonOutputParser
from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field

from core.config import get_settings

# LLM 초기화 (환경에 맞게 모델 변경 가능, 예: gpt-4o 또는 gemini-pro)
# 실제 환경에서는 의존성 주입(Dependency Injection) 등으로 관리하는 것이 좋습니다.
# 1. 질문과 응답의 일치도 및 표현 품질 확인 (Q&A Alignment)
class QAAlignmentOutput(BaseModel):
    score: int = Field(description="1에서 100 사이의 점수")
    feedback: str = Field(description="평가 이유")


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

qa_alignment_prompt = ChatPromptTemplate.from_messages([
    ("system", """당신은 엄격한 면접관이자 자기소개서 편집자입니다.
질문의 핵심 의도와 답변의 일치도를 100점 만점으로 평가하세요.
피드백에는 내용의 구체성·논리성뿐 아니라 맞춤법, 띄어쓰기, 문법, 어색하거나 모호한 표현도 함께 지적하고 개선 방향을 제안하세요.
답변 전체를 교정한 별도 원고는 만들지 마세요.
반드시 score는 정수, feedback은 중첩 객체가 아닌 하나의 문자열인 JSON 객체로 반환하세요."""),
    ("user", "질문: {question}\n\n답변: {content}")
])

# 2. 공고와 자기소개서가 일치하는 지 확인 (JD Alignment)
jd_fit_prompt = ChatPromptTemplate.from_messages([
    ("system", """당신은 채용 담당자(HR)이자 자기소개서 편집자입니다.
채용 공고의 필수 요건과 우대 사항을 기준으로 자기소개서가 직무에 적합한 역량을 보여주는지 분석하세요.
부족한 근거와 보완할 키워드를 제안하고, 직무 적합성을 약화하는 맞춤법·문법 오류나 모호하고 어색한 표현도 피드백에 포함하세요."""),
    ("user", "채용 공고: {job_description}\n\n자기소개서: {content}")
])

# 3. 피드백을 반영한 최종 첨삭 (Custom Criteria Editing)
final_edit_prompt = ChatPromptTemplate.from_messages([
    ("system", """당신은 전문 취업 컨설턴트이자 교정 편집자입니다.
주어진 분석을 바탕으로 자기소개서를 최종 첨삭하세요.
원문의 사실과 경험을 임의로 추가하지 말고, 맞춤법·띄어쓰기·문법·어색한 표현을 바로잡으면서 가독성과 설득력을 높이세요.
설명이나 변경 목록 없이 완성된 최종본만 반환하세요."""),
    ("user", """
원본 질문: {question}
원본 내용: {content}
질문-응답 일치도 분석: {qa_alignment_feedback}
직무 적합도 분석: {jd_fit_feedback}

위 내용을 종합하여 가장 완벽하고 설득력 있는 자기소개서 최종본을 작성해 주세요.
""")
])

overall_review_prompt = ChatPromptTemplate.from_messages([
    ("system", """당신은 채용 담당자이자 자기소개서 전문 컨설턴트입니다.
여러 문항의 원문과 문항별 분석 결과를 함께 검토하여 자기소개서 전체의 완성도를 평가하세요.
문항 하나의 표현을 반복하지 말고, 문항 간 일관성·경험의 중복·직무 적합성·근거의 구체성을 종합하세요.
원문에 없는 사실을 만들지 마세요.
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
    return (
        qa_alignment_prompt | llm | JsonOutputParser(pydantic_object=QAAlignmentOutput),
        jd_fit_prompt | llm | StrOutputParser(),
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
    return overall_review_prompt | llm | JsonOutputParser(
        pydantic_object=OverallReviewOutput
    )


def _process_cover_letter_with_chains(
    question: str,
    content: str,
    job_description: str,
    chains: tuple,
) -> dict:
    qa_alignment_chain, jd_fit_chain, final_edit_chain = chains

    qa_result = qa_alignment_chain.invoke({
        "question": question,
        "content": content,
    })
    qa_alignment_feedback = _feedback_to_text(qa_result.get("feedback"))

    jd_fit_feedback = "제공된 채용 공고 정보가 없습니다."
    if job_description:
        jd_fit_feedback = jd_fit_chain.invoke({
            "job_description": job_description,
            "content": content,
        })

    final_edited_content = final_edit_chain.invoke({
        "question": question,
        "content": content,
        "qa_alignment_feedback": qa_alignment_feedback,
        "jd_fit_feedback": jd_fit_feedback,
    })

    return {
        "qa_alignment_score": qa_result.get("score", 0),
        "qa_alignment_feedback": qa_alignment_feedback,
        "jd_fit_feedback": jd_fit_feedback,
        "final_edited_content": final_edited_content,
    }

def process_cover_letter_chain(question: str, content: str, job_description: str = "") -> dict:
    """Run the existing single-item cover-letter editing pipeline."""
    return _process_cover_letter_with_chains(
        question,
        content,
        job_description,
        _create_cover_letter_chains(),
    )

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

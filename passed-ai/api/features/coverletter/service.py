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

qa_alignment_prompt = ChatPromptTemplate.from_messages([
    ("system", """당신은 엄격한 면접관이자 자기소개서 편집자입니다.
질문의 핵심 의도와 답변의 일치도를 100점 만점으로 평가하세요.
피드백에는 내용의 구체성·논리성뿐 아니라 맞춤법, 띄어쓰기, 문법, 어색하거나 모호한 표현도 함께 지적하고 개선 방향을 제안하세요.
답변 전체를 교정한 별도 원고는 만들지 말고 JSON 형태로 반환하세요."""),
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

def process_cover_letter_chain(question: str, content: str, job_description: str = "") -> dict:
    qa_alignment_chain, jd_fit_chain, final_edit_chain = (
        _create_cover_letter_chains()
    )

    """
    LangChain을 활용한 자기소개서 첨삭 파이프라인.
    """
    # Step 1: 원문을 기준으로 질문 적합도와 표현 품질을 함께 평가
    qa_result = qa_alignment_chain.invoke({
        "question": question,
        "content": content,
    })
    
    # Step 2: 직무(공고) 적합도와 표현상 보완점을 함께 분석
    jd_fit_feedback = "제공된 채용 공고 정보가 없습니다."
    if job_description:
        jd_fit_feedback = jd_fit_chain.invoke({
            "job_description": job_description,
            "content": content,
        })
        
    # Step 3: 두 피드백과 교정 사항을 반영해 최종본으로 재작성
    final_edited_content = final_edit_chain.invoke({
        "question": question,
        "content": content,
        "qa_alignment_feedback": qa_result.get("feedback", ""),
        "jd_fit_feedback": jd_fit_feedback
    })
    
    return {
        "qa_alignment_score": qa_result.get("score", 0),
        "qa_alignment_feedback": qa_result.get("feedback", ""),
        "jd_fit_feedback": jd_fit_feedback,
        "final_edited_content": final_edited_content
    }

# 전체 자기소개서를 읽고 리뷰하는 기능
def process_cover_letter_review_chain(question_list: list, content_list: list, job_description: str = "") -> dict:
    """
    Review multiple cover-letter items once the batch review flow is implemented.
    """
    raise NotImplementedError("Multi-item cover-letter review is not implemented yet.")

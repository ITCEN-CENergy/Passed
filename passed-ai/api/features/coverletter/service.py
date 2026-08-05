from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser, JsonOutputParser
from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field
import os

from core.config import get_settings

settings = get_settings()

# LLM 초기화 (환경에 맞게 모델 변경 가능, 예: gpt-4o 또는 gemini-pro)
# 실제 환경에서는 의존성 주입(Dependency Injection) 등으로 관리하는 것이 좋습니다.
llm = ChatOpenAI(
    api_key=settings.openai_api_key,
    model="gpt-4o-mini",
    temperature=0.2
)

# 1. 맞춤법 검사 (Spell & Grammar Check)
spell_check_prompt = ChatPromptTemplate.from_messages([
    ("system", "당신은 교정 전문가입니다. 주어진 텍스트의 의미를 변경하지 말고 맞춤법, 띄어쓰기, 어색한 문법만을 완벽하게 교정하여 반환하세요. 다른 설명은 덧붙이지 마세요."),
    ("user", "{content}")
])
spell_check_chain = spell_check_prompt | llm | StrOutputParser()

# 2. 질문과 응답이 일치하는 지 확인 (Q&A Alignment)
class QAAlignmentOutput(BaseModel):
    score: int = Field(description="1에서 100 사이의 점수")
    feedback: str = Field(description="평가 이유")

qa_alignment_prompt = ChatPromptTemplate.from_messages([
    ("system", "당신은 엄격한 면접관입니다. 다음 질문의 핵심 의도를 파악하고, 답변이 그 의도를 충족하는지 100점 만점으로 평가하고 이유를 간략히 설명하세요. JSON 형태로 반환하세요."),
    ("user", "질문: {question}\n\n답변: {spell_checked_content}")
])
qa_alignment_chain = qa_alignment_prompt | llm | JsonOutputParser(pydantic_object=QAAlignmentOutput)

# 3. 공고와 자기소개서가 일치하는 지 확인 (JD Alignment)
jd_fit_prompt = ChatPromptTemplate.from_messages([
    ("system", "당신은 채용 담당자(HR)입니다. 채용 공고의 필수 요건과 우대 사항을 기준으로, 자소서 내용이 직무에 적합한 역량을 보여주는지 분석하세요. 부족한 키워드나 보완할 점을 제안하세요."),
    ("user", "채용 공고: {job_description}\n\n자기소개서: {spell_checked_content}")
])
jd_fit_chain = jd_fit_prompt | llm | StrOutputParser()

# 4. 자체적인 기준으로 첨삭 (Custom Criteria Editing)
final_edit_prompt = ChatPromptTemplate.from_messages([
    ("system", "당신은 전문 취업 컨설턴트입니다. 주어진 분석 결과들을 바탕으로 자기소개서를 최종 첨삭하세요. 가독성을 높이고 지원자의 강점이 돋보이도록 문장을 재작성하고, 완성된 최종본만 반환하세요."),
    ("user", """
원본 질문: {question}
원본 내용 (맞춤법 교정됨): {spell_checked_content}
질문-응답 일치도 분석: {qa_alignment_feedback}
직무 적합도 분석: {jd_fit_feedback}

위 내용을 종합하여 가장 완벽하고 설득력 있는 자기소개서 최종본을 작성해 주세요.
""")
])
final_edit_chain = final_edit_prompt | llm | StrOutputParser()

def process_cover_letter_chain(question: str, content: str, job_description: str = "") -> dict:
    """
    LangChain을 활용한 자기소개서 첨삭 파이프라인.
    """
    # Step 1: 맞춤법 검사
    spell_checked_content = spell_check_chain.invoke({"content": content})
    
    # Step 2: 질문-응답 일치도
    qa_result = qa_alignment_chain.invoke({
        "question": question,
        "spell_checked_content": spell_checked_content,
    })
    
    # Step 3: 직무(공고) 적합도 분석
    jd_fit_feedback = "제공된 채용 공고 정보가 없습니다."
    if job_description:
        jd_fit_feedback = jd_fit_chain.invoke({
            "job_description": job_description,
            "spell_checked_content": spell_checked_content
        })
        
    # Step 4: 종합 첨삭(재작성)
    final_edited_content = final_edit_chain.invoke({
        "question": question,
        "spell_checked_content": spell_checked_content,
        "qa_alignment_feedback": qa_result.get("feedback", ""),
        "jd_fit_feedback": jd_fit_feedback
    })
    
    return {
        "spell_checked_content": spell_checked_content,
        "qa_alignment_score": qa_result.get("score", 0),
        "qa_alignment_feedback": qa_result.get("feedback", ""),
        "jd_fit_feedback": jd_fit_feedback,
        "final_edited_content": final_edited_content
    }

"""이력서 source_type과 자기소개서 question_type별 스킬 추출 프롬프트."""

from __future__ import annotations

from .skill_extraction_models import ExtractableChunk


SYSTEM_PROMPT = """당신은 채용 문서에서 검증 가능한 역량 후보만 추출하는 분석가입니다.

반드시 다음 네 카테고리 중 하나를 사용하세요.
- TECHNICAL_SKILL: 프로그래밍 언어, 프레임워크, DB, 도구, 기술 개념
- EXPERIENCE: 직무, 산업, 프로젝트, 역할, 업무 수행 경험
- BEHAVIORAL_TRAIT: 협업, 소통, 리더십, 문제 해결처럼 행동으로 확인되는 특성
- CERTIFICATION: 공식 자격증, 면허, 공인 시험 자격

규칙:
1. 원문에 명시되었거나 구체적인 행동으로 직접 입증된 후보만 추출합니다.
2. evidence는 원문에 실제로 존재하는 연속된 문구를 글자 하나 바꾸지 않고 사용합니다.
3. extracted_name은 evidence로 입증되는 역량 하나를 짧게 원자화합니다.
   - 조사와 어미는 정리할 수 있습니다. 예: "일정을 조율함" → "일정 조율"
   - 기술 여러 개나 행동 여러 개를 한 후보로 묶지 않습니다.
   - 문장 전체, 성과 수치 전체, 미래 계획을 이름으로 사용하지 않습니다.
   - evidence에 없는 역량이나 상위 개념을 새로 만들어내지 않습니다.
   - "REST API를 설계하고 개발"은 "REST API", "API 설계", "API 개발"처럼
     기술 대상과 서로 다른 수행 경험으로 나눕니다.
   - "AWS EC2, S3, RDS 사용"은 서로 다른 세 기술 후보로 나눕니다.
4. 지원 의지, 희망, 포부, 입사 후 계획만으로 현재 보유 역량을 추론하지 않습니다.
   "하고 싶습니다", "되겠습니다", "하겠습니다", "만들겠습니다"처럼 미래만 말하는
   문장은 후보를 만들지 않습니다. 아직 하지 않은 일은 수행 경험이 아닙니다.
   "이 경험을 바탕으로 ~하고 싶습니다"처럼 과거 경험을 언급하더라도 문장의
   주된 내용이 미래 의도라면 후보를 만들지 않습니다.
5. 회사·학교·기관·교육 과정·행사·대회명은 그 자체로 후보가 아닙니다. 그 안에서
   실제로 사용한 기술, 수행한 업무, 역할과 행동만 추출합니다. 단, React, PostgreSQL,
   AWS처럼 실제 사용이 명시된 기술 제품·도구·플랫폼은 TECHNICAL_SKILL입니다.
6. 자격증은 취득·보유가 명시된 경우만 추출합니다. 필기 합격, 준비 중, 취득 예정은
   보유가 아니므로 추출하지 않습니다. 한 문장에 여러 자격증이 있으면 각각 나눕니다.
7. 같은 이름과 카테고리의 후보를 중복 출력하지 않습니다.
8. level은 원문 근거의 깊이만 보고 정합니다.
   - 1: 언급, 학습, 단순 참여 또는 기본 사용
   - 2: 실제 과업·프로젝트에서 독립적으로 적용
   - 3: 복잡한 문제 해결, 설계·최적화·리딩 또는 수치로 확인되는 성과
9. CERTIFICATION은 보유 후보만 추출하고 level을 항상 1로 반환합니다. 미보유는 후보를 만들지 않습니다.
10. 근거가 없으면 skills를 빈 배열로 반환합니다.
11. 한 청크에서 중요도가 높은 후보만 최대 12개 반환합니다.
"""


_RESUME_CONTEXT_GUIDANCE = {
    "CERTIFICATION": (
        "취득·보유한 자격증과 공인 시험 성적만 CERTIFICATION으로 추출합니다. "
        "필기 합격이나 준비 중인 자격은 제외하고, 자격증만 보고 관련 기술의 "
        "실무 숙련도를 추가로 추론하지 마세요."
    ),
    "EXPERIENCE": (
        "사용한 기술, 수행 업무, 역할, 성과를 우선 추출합니다. 구체적 행동으로 "
        "드러난 행동 특성만 BEHAVIORAL_TRAIT로 추출하세요."
    ),
    "TRAINING": (
        "실제로 학습하거나 프로젝트에서 사용한 기술과 교육 경험을 추출합니다. "
        "교육 과정 이름만으로 배우지 않은 기술을 추론하지 마세요."
    ),
    "EDUCATION": "전공·학업 경험과 명시된 기술만 추출하고 학교 이름은 스킬로 만들지 마세요.",
    "ACTIVITY": "행사·동아리·대회 이름은 제외하고 실제 수행 업무, 역할, 기술과 행동만 추출하세요.",
    "AWARD": "대회명과 상 이름은 제외하고 수상 근거가 된 실제 수행 경험만 추출하세요.",
}

_COVER_LETTER_CONTEXT_GUIDANCE = {
    "MOTIVATION": (
        "미래 포부와 희망은 제외하고, 이미 수행한 경험과 사용한 기술만 후보로 추출하세요."
    ),
    "PERSONALITY": (
        "성격을 나타내는 단어만 보지 말고 실제 상황·행동이 함께 제시된 "
        "BEHAVIORAL_TRAIT만 추출하세요."
    ),
    "EXPERIENCE": (
        "상황·행동·역할·성과에서 기술, 직무 경험, 행동 특성을 균형 있게 추출하세요."
    ),
}


def build_user_prompt(chunk: ExtractableChunk) -> str:
    """문서 종류와 문맥 유형을 명시해 같은 단어의 과잉 해석을 줄인다."""
    if chunk.source_kind == "RESUME":
        guidance = _RESUME_CONTEXT_GUIDANCE.get(
            chunk.context_type,
            "해당 이력서 항목에 명시된 기술과 검증 가능한 경험만 추출하세요.",
        )
        context_label = "이력서 source_type"
    elif chunk.source_kind == "COVER_LETTER":
        guidance = _COVER_LETTER_CONTEXT_GUIDANCE.get(
            chunk.context_type,
            "자기소개서에서 과거 행동으로 확인되는 후보만 추출하세요.",
        )
        context_label = "자기소개서 question_type"
    else:
        raise ValueError(f"지원하지 않는 문서 종류입니다: {chunk.source_kind}")

    return (
        f"문서 종류: {chunk.source_kind}\n"
        f"{context_label}: {chunk.context_type}\n"
        f"문맥별 주의사항: {guidance}\n\n"
        f"원문:\n{chunk.chunk_content}"
    )

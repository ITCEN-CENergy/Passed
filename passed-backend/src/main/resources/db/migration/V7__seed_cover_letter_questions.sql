-- 자기소개서 고정 질문의 초기 데이터입니다.
-- 질문 문구를 변경할 때는 이 파일을 수정하지 말고 새로운 마이그레이션을 추가합니다.

INSERT INTO cover_letter_questions (
    question_type,
    question_text,
    guide_text,
    match_weight,
    display_order,
    is_active
)
VALUES
    (
        'MOTIVATION',
        '지원하려는 직무와 회사를 선택한 이유와 입사 후 이루고 싶은 목표를 작성해 주세요.',
        '회사와 직무를 선택한 구체적인 이유, 본인의 경험과 직무의 연결점, 입사 후 기여하고 싶은 내용을 중심으로 작성해 주세요.',
        0.60,
        1,
        TRUE
    ),
    (
        'PERSONALITY',
        '본인의 성격상 강점과 보완할 점을 실제 사례를 중심으로 작성해 주세요.',
        '강점이 드러난 상황과 행동, 결과를 구체적으로 설명하고 보완점은 개선을 위해 실천 중인 방법과 함께 작성해 주세요.',
        0.20,
        2,
        TRUE
    ),
    (
        'EXPERIENCE',
        '지원 직무와 관련된 경험 중 가장 의미 있었던 경험과 본인의 역할 및 성과를 작성해 주세요.',
        '상황, 해결해야 했던 과제, 본인이 수행한 행동, 결과의 순서로 작성하고 가능하면 성과를 수치나 객관적인 근거로 표현해 주세요.',
        1.00,
        3,
        TRUE
    )
ON CONFLICT (question_type) DO NOTHING;

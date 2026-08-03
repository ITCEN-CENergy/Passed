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
        '지원 동기와 입사 후 이루고 싶은 목표를 작성해 주세요.',
        '회사와 직무를 선택한 이유, 입사 후 기여하고 싶은 내용을 구체적으로 작성해 주세요.',
        0.60,
        1,
        true
    ),
    (
        'PERSONALITY',
        '본인의 성격과 강점 및 보완점을 작성해 주세요.',
        '실제 행동 사례를 바탕으로 업무 방식과 협업 특성이 드러나도록 작성해 주세요.',
        0.20,
        2,
        true
    ),
    (
        'EXPERIENCE',
        '지원 직무와 관련된 경험과 성과를 작성해 주세요.',
        '상황, 맡은 역할, 행동, 정량적 또는 정성적 결과 순서로 구체적으로 작성해 주세요.',
        1.00,
        3,
        true
    )
ON CONFLICT (question_type) DO NOTHING;

-- 실제 사용자 preview에서 확인된 마스터 누락과 명확한 표현 차이만 보강합니다.
INSERT INTO skills (name, category, description)
VALUES (
    'Machine Learning',
    'TECHNICAL_SKILL',
    '데이터에서 패턴을 학습하여 분류·예측·추천 등의 문제를 해결하는 모델을 개발하고 평가하는 기술입니다.'
)
ON CONFLICT (name) DO NOTHING;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, source)
SELECT s.id, seed.alias, seed.normalized_alias, 'REVIEWED'
FROM (
    VALUES
        ('REST API', 'TECHNICAL_SKILL', 'REST API 설계', 'restapi설계'),
        ('REST API', 'TECHNICAL_SKILL', 'REST API 개발', 'restapi개발'),
        ('협업', 'BEHAVIORAL_TRAIT', '일정 조율', '일정조율'),
        ('추천 시스템 프로젝트', 'EXPERIENCE', '추천 시스템', '추천시스템'),
        ('트랜잭션 처리', 'EXPERIENCE', '트랜잭션 범위 조정', '트랜잭션범위조정'),
        ('Machine Learning', 'TECHNICAL_SKILL', '머신러닝', '머신러닝')
) AS seed(skill_name, category, alias, normalized_alias)
JOIN skills s ON s.name = seed.skill_name AND s.category = seed.category
ON CONFLICT (skill_id, normalized_alias) DO NOTHING;

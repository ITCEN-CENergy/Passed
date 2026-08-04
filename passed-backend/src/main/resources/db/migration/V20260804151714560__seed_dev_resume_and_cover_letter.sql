-- ============================================================================
-- 개발 환경에서 이력서·자기소개서 청킹 파이프라인을 확인하기 위한 테스트 데이터입니다.
-- ============================================================================
INSERT INTO users (name, email, password, field, desired_jobs)
VALUES (
    '테스트사용자',
    'test@passed.dev',
    'dev-only-not-hashed',
    '백엔드 개발',
    '["백엔드 개발자"]'::jsonb
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO resumes (user_id)
SELECT id
FROM users
WHERE email = 'test@passed.dev'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO experiences (
    resume_id,
    company_name,
    position,
    responsibilities,
    career_desc,
    start_date,
    end_date,
    is_working
)
SELECT
    r.id,
    '카카오',
    '백엔드 개발자',
    'REST API 설계 및 개발',
    'Java와 Spring Boot로 결제 시스템의 결제 API를 설계하고 개발함. 팀장을 맡아 4명의 일정을 조율함.',
    DATE '2023-03-01',
    DATE '2024-08-31',
    FALSE
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'test@passed.dev'
  AND NOT EXISTS (
      SELECT 1
      FROM experiences e
      WHERE e.resume_id = r.id
        AND e.company_name = '카카오'
        AND e.position = '백엔드 개발자'
        AND e.start_date = DATE '2023-03-01'
  );

INSERT INTO trainings (
    resume_id,
    name,
    institution,
    start_date,
    end_date,
    description
)
SELECT
    r.id,
    'AI 부트캠프',
    '패스트캠퍼스',
    DATE '2022-09-01',
    DATE '2023-02-28',
    'Python과 머신러닝 기초를 학습하고 팀 프로젝트로 추천 시스템을 구현함.'
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'test@passed.dev'
  AND NOT EXISTS (
      SELECT 1
      FROM trainings t
      WHERE t.resume_id = r.id
        AND t.name = 'AI 부트캠프'
        AND t.institution = '패스트캠퍼스'
        AND t.start_date = DATE '2022-09-01'
  );

INSERT INTO certifications (
    resume_id,
    name,
    issuer,
    acquisition_date
)
SELECT
    r.id,
    '정보처리기사',
    '한국산업인력공단',
    DATE '2023-06-01'
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'test@passed.dev'
  AND NOT EXISTS (
      SELECT 1
      FROM certifications c
      WHERE c.resume_id = r.id
        AND c.name = '정보처리기사'
        AND c.issuer = '한국산업인력공단'
        AND c.acquisition_date = DATE '2023-06-01'
  );

INSERT INTO cover_letters (user_id)
SELECT id
FROM users
WHERE email = 'test@passed.dev'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO cover_letter_items (cover_letter_id, question_id, answer)
SELECT
    cl.id,
    q.id,
    $answer$
대학 시절 학생회 활동을 하며 협업의 중요성을 배웠습니다. 서로 다른 의견을 조율하는 과정에서 경청하는 태도가 결과를 바꾼다는 것을 알게 되었습니다.

부트캠프에서는 Java와 Spring Boot로 헬스장 매칭 서비스를 개발했습니다. 팀장을 맡아 4명의 일정을 조율했고, API 응답 시간을 40% 개선했습니다. PostgreSQL 실행 계획을 분석해 불필요한 전체 테이블 조회를 줄이고 자주 사용하는 검색 조건에 인덱스를 적용했습니다. 장애가 발생했을 때는 로그와 재현 테스트를 먼저 작성해 원인을 좁혔고, 트랜잭션 범위를 조정하여 동일 문제가 다시 발생하지 않도록 했습니다.

이 경험을 바탕으로 백엔드 개발자로 성장하고 싶습니다. 사용자의 이력서와 자기소개서에서 근거를 정확히 추적하고, 결과가 달라졌을 때 어떤 원본이 영향을 주었는지 설명할 수 있는 서비스를 만들겠습니다.
$answer$
FROM cover_letters cl
JOIN users u ON u.id = cl.user_id
JOIN cover_letter_questions q ON q.question_type = 'EXPERIENCE'
WHERE u.email = 'test@passed.dev'
ON CONFLICT (cover_letter_id, question_id) DO NOTHING;

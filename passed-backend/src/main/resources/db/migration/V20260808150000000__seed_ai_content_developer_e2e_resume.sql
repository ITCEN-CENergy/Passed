-- ============================================================================
-- 사용자 스킬 전체 파이프라인 E2E 검증용 이력서입니다.
-- 식별 이메일: ai.test2@example.com
-- 청크와 임베딩은 저장하지 않고 원본 이력서 데이터만 적재합니다.
-- ============================================================================

INSERT INTO users (name, email, password, field, desired_jobs)
VALUES (
    '김테스트',
    'ai.test2@example.com',
    'e2e-only-not-hashed',
    'AI·개발',
    '["AI 서비스 개발자"]'::jsonb
)
ON CONFLICT (email) DO UPDATE SET
    name = EXCLUDED.name,
    password = EXCLUDED.password,
    field = EXCLUDED.field,
    desired_jobs = EXCLUDED.desired_jobs;

INSERT INTO resumes (user_id)
SELECT id
FROM users
WHERE email = 'ai.test2@example.com'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO personal_infos (resume_id, email, phone, address)
SELECT
    r.id,
    'ai.test2@example.com',
    '010-1234-5678',
    '대전광역시 유성구'
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'ai.test2@example.com'
ON CONFLICT (resume_id) DO UPDATE SET
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    address = EXCLUDED.address;

INSERT INTO educations (
    resume_id,
    school_type,
    school_name,
    admission_date,
    graduation_date,
    status,
    is_transfer,
    major_name,
    gpa,
    max_gpa
)
SELECT
    r.id,
    '4년제 대학교',
    '테스트대학교',
    DATE '2020-03-01',
    DATE '2024-02-29',
    '졸업',
    FALSE,
    '컴퓨터공학',
    4.10,
    4.50
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'ai.test2@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM educations e
      WHERE e.resume_id = r.id
        AND e.school_name = '테스트대학교'
        AND e.admission_date = DATE '2020-03-01'
  );

INSERT INTO experiences (
    resume_id,
    company_name,
    department_name,
    start_date,
    end_date,
    is_working,
    position,
    responsibilities,
    career_desc
)
SELECT
    r.id,
    '테스트AI랩',
    'AI 플랫폼 개발팀',
    DATE '2024-03-01',
    DATE '2024-08-31',
    FALSE,
    'AI 서비스 개발 인턴',
    'TypeScript와 JavaScript를 사용하여 콘텐츠 생성 AI 서비스의 백엔드 API를 개발했습니다.',
    $description$
OpenAI LLM API를 연동하여 사용자의 요청에 따라 콘텐츠를 생성하는 기능을 구현했으며, 사내 문서를 기반으로 더 정확한 결과를 생성하기 위해 RAG 파이프라인을 개발했습니다.

문서를 청킹한 뒤 임베딩을 생성하고 Vector DB에 저장했으며, 사용자 요청과 관련된 문서를 검색하여 LLM 프롬프트에 전달하는 검색 증강 생성 구조를 구현했습니다.

생성형 AI의 답변 품질을 검증하기 위해 골든셋을 구축하고 Precision, Recall, F1 기반의 평가 로직을 개발했습니다. 프롬프트를 기능별로 분리하여 버전 관리하고, 프롬프트 변경 전후의 생성 결과를 비교하여 품질 저하 여부를 확인했습니다.

LLM API 호출 횟수와 토큰 사용량을 분석하여 불필요한 요청을 줄이고 프롬프트 길이를 최적화해 AI API 사용 비용을 절감했습니다. 개인정보가 포함된 요청이 LLM에 그대로 전달되지 않도록 입력 단계에 개인정보 탐지 및 보안 필터를 적용했습니다.

서비스 로그와 사용자 피드백을 수집하여 반복적으로 발생하는 생성 실패 유형을 분석하고 프롬프트와 검색 로직을 개선했습니다. 장애 발생 시 로그, 요청 데이터, 재현 절차를 기준으로 원인을 추적했으며 임시 조치와 근본 개선 작업을 구분하여 처리했습니다.

현업 콘텐츠 운영팀과 기능 우선순위를 조율하고, 요구사항 변경 이유와 기술적 결정 내용을 문서화하여 팀원들과 공유했습니다. AWS Cloud 환경에 서비스를 배포하고 서비스 상태와 LLM API 오류를 모니터링했습니다.
$description$
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'ai.test2@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM experiences e
      WHERE e.resume_id = r.id
        AND e.company_name = '테스트AI랩'
        AND e.position = 'AI 서비스 개발 인턴'
        AND e.start_date = DATE '2024-03-01'
  );

INSERT INTO activities (
    resume_id,
    activity_type,
    organization,
    start_date,
    end_date,
    description
)
SELECT
    r.id,
    '팀 프로젝트',
    '테스트대학교 AI 프로젝트팀',
    DATE '2023-09-01',
    DATE '2024-01-31',
    $description$
생성형 AI 기반 콘텐츠 생성 플랫폼을 개발했습니다. 컴퓨터공학 전공 과정에서 학습한 JavaScript, TypeScript, 데이터베이스, 머신러닝, 클라우드 컴퓨팅을 적용했습니다.

사용자가 주제를 입력하면 관련 내부 문서를 검색하고 참고 자료를 기반으로 콘텐츠를 생성하도록 OpenAI LLM API와 API 서버를 연동했습니다. RAG 파이프라인을 설계하여 문서를 청킹하고 임베딩한 뒤 Vector DB에서 관련 데이터를 검색하도록 구현했습니다.

검색 결과와 사용자 질문을 조합한 프롬프트를 설계하고 프롬프트별 결과를 비교했습니다. 검색 결과가 부족한 경우 답변 생성을 제한하는 가드레일과 보안 필터를 적용했습니다.

골든셋으로 생성 결과와 검색 결과를 평가하고 사용자 피드백을 기능 우선순위에 반영했습니다. 역할 분담, 기술 선택 이유, API 명세와 프롬프트 변경 이력을 문서화하여 팀원들과 공유했습니다.
$description$
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'ai.test2@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM activities a
      WHERE a.resume_id = r.id
        AND a.organization = '테스트대학교 AI 프로젝트팀'
        AND a.start_date = DATE '2023-09-01'
  );

INSERT INTO activities (
    resume_id,
    activity_type,
    organization,
    start_date,
    end_date,
    description
)
SELECT
    r.id,
    '팀 프로젝트',
    '테스트AI랩',
    DATE '2024-05-01',
    DATE '2024-07-31',
    $description$
AI 서비스 장애 분석 및 성능 개선 프로젝트에서 콘텐츠 생성 응답 시간이 증가하고 일부 요청이 실패하는 문제를 해결했습니다.

서비스 로그와 LLM API 응답 데이터를 분석하여 동일 문서의 반복 임베딩과 불필요하게 긴 프롬프트가 원인임을 확인했습니다. 콘텐츠 해시로 변경되지 않은 문서는 임베딩을 다시 생성하지 않도록 개선하고 RAG 검색 범위를 조정했습니다.

프롬프트의 불필요한 내용을 제거하여 토큰 사용량과 API 비용을 줄이고 응답 속도를 개선했습니다. 장애 원인, 임시 해결 방법, 근본 개선 방법과 재발 방지 내용을 문서화했습니다. 중복된 LLM API 호출과 프롬프트 로직을 기술 부채로 정의하고 공통 모듈로 단계적으로 분리했습니다.
$description$
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'ai.test2@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM activities a
      WHERE a.resume_id = r.id
        AND a.organization = '테스트AI랩'
        AND a.start_date = DATE '2024-05-01'
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
    '생성형 AI 서비스 개발 과정',
    '테스트AI아카데미',
    DATE '2024-09-01',
    DATE '2025-02-28',
    $description$
LLM API, RAG, Vector DB, 프롬프트 엔지니어링, 머신러닝, 생성형 AI 서비스 설계 및 평가 방법을 학습했습니다.

JavaScript와 TypeScript로 LLM API 기반 AI 서비스를 개발하고 임베딩과 Vector DB를 이용한 RAG 파이프라인을 구현했습니다. AWS와 Microsoft Azure Cloud 환경에서 서비스를 배포하고 모니터링했습니다. 개인정보 보호, 입력 검증, 보안 필터와 비용 최적화 방법을 학습했습니다.
$description$
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'ai.test2@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM trainings t
      WHERE t.resume_id = r.id
        AND t.name = '생성형 AI 서비스 개발 과정'
        AND t.institution = '테스트AI아카데미'
        AND t.start_date = DATE '2024-09-01'
  );

INSERT INTO certifications (resume_id, name, issuer, acquisition_date)
SELECT r.id, seed.name, seed.issuer, seed.acquisition_date
FROM resumes r
JOIN users u ON u.id = r.user_id
CROSS JOIN (
    VALUES
        ('AWS Certified AI Practitioner', 'Amazon Web Services', DATE '2025-03-15'),
        ('Microsoft Certified: Azure AI Engineer Associate', 'Microsoft', DATE '2025-06-20'),
        ('빅데이터분석기사', '한국데이터산업진흥원', DATE '2025-09-12')
) AS seed(name, issuer, acquisition_date)
WHERE u.email = 'ai.test2@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM certifications c
      WHERE c.resume_id = r.id
        AND c.name = seed.name
        AND c.issuer = seed.issuer
        AND c.acquisition_date = seed.acquisition_date
  );

INSERT INTO awards (resume_id, name, issuer, award_date, description)
SELECT
    r.id,
    '생성형 AI 서비스 개발 우수 프로젝트상',
    '테스트AI아카데미',
    DATE '2025-02-28',
    'LLM API, RAG, Vector DB를 활용하여 콘텐츠 생성 서비스를 구현하고, 골든셋 기반 평가와 프롬프트 개선으로 생성 결과의 품질을 높인 점을 인정받아 우수 프로젝트로 선정되었습니다.'
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'ai.test2@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM awards a
      WHERE a.resume_id = r.id
        AND a.name = '생성형 AI 서비스 개발 우수 프로젝트상'
        AND a.award_date = DATE '2025-02-28'
  );

INSERT INTO language_proficiencies (resume_id, language_name, proficiency_level)
SELECT
    r.id,
    '영어',
    'BUSINESS'
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'ai.test2@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM language_proficiencies lp
      WHERE lp.resume_id = r.id
        AND lp.language_name = '영어'
  );

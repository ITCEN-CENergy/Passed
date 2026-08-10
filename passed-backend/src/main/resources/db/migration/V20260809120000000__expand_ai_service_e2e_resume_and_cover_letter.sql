-- ============================================================================
-- 사용자 56(ai.test2@example.com)의 4715~4730 통합 E2E 검증용 문서 보강입니다.
-- 실제 수행 문장에 포함된 역량만 스킬 추출 근거로 사용할 수 있도록 구성합니다.
-- 추천 점수, 추천 필터, ranking, skill_relations 데이터는 변경하지 않습니다.
-- 청크와 임베딩은 스킬 분석 파이프라인이 원문 변경을 감지해 갱신합니다.
-- ============================================================================

UPDATE experiences e
SET responsibilities =
        'TypeScript와 JavaScript를 사용하여 상용 콘텐츠 생성 AI 서비스의 백엔드 API를 개발하고 운영했습니다.',
    career_desc = $description$
LLM을 활용한 생성형 AI 기능을 설계하고 OpenAI LLM API를 연동하여 상용 서비스 기능으로 구현했습니다. 사내 문서를 기반으로 RAG 파이프라인과 문서 검색 기능을 개발하고, 문서를 청킹한 뒤 임베딩하여 Vector DB에 저장했습니다.

AWS 클라우드 환경에 기능을 배포하고 서비스 상태와 LLM API 오류를 모니터링했습니다. 서비스 로그와 요청 데이터를 분석해 응답 지연과 호출 실패의 원인을 추적하고, 프롬프트 길이와 반복 임베딩을 줄여 성능·비용·안정성을 개선했습니다.

사용자 피드백을 분석해 검색 기준과 콘텐츠 생성 기능을 개선했습니다. 요구사항, API 명세, 기술적 결정, 장애 원인과 재발 방지 대책을 업무 문서로 작성하고 팀원들과 공유했습니다.

개인정보 보호와 보안 기준을 준수하고 정보보호 의식을 바탕으로 API 키와 민감정보를 환경변수로 관리했습니다. 개인정보가 외부 LLM에 전달되지 않도록 개인정보 탐지와 보안 필터를 적용했습니다.
$description$
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE e.resume_id = r.id
  AND u.email = 'ai.test2@example.com'
  AND e.company_name = '테스트AI랩'
  AND e.position = 'AI 서비스 개발 인턴'
  AND e.start_date = DATE '2024-03-01';

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
    seed.activity_type,
    seed.organization,
    seed.start_date,
    seed.end_date,
    seed.description
FROM resumes r
JOIN users u ON u.id = r.user_id
CROSS JOIN (
    VALUES
        (
            '팀 프로젝트',
            '통합테스트 RAG AI 챗봇팀',
            DATE '2024-09-01',
            DATE '2024-10-15',
            $project$
RAG AI 챗봇 프로젝트에서 Python과 FastAPI를 사용하여 AI 챗봇 백엔드 API를 개발했습니다.

LangChain으로 RAG 파이프라인을 구현하고 문서를 임베딩하여 Vector DB에 저장했습니다. 문서 검색 결과를 LLM에 전달하고 OpenAI LLM API를 연동해 근거 기반 답변을 생성했습니다.

Docker로 FastAPI 서비스를 컨테이너화하고 AWS 클라우드 환경에 기능을 배포했습니다. 배포 후 로그와 응답 시간을 모니터링하며 상용 서비스 개발과 운영 절차를 경험했습니다.
$project$
        ),
        (
            '팀 프로젝트',
            '통합테스트 채용 추천 서비스팀',
            DATE '2024-10-16',
            DATE '2024-11-30',
            $project$
추천 서비스 프로젝트에서 사용자의 보유 스킬과 채용 공고의 요구 스킬을 비교하여 개인화된 채용 공고를 제공하는 추천 서비스를 개발했습니다.

사용자 피드백을 분석하여 추천 기준과 기능을 개선하고 사용자 피드백 기반 제품 개선을 수행했습니다. 추천 결과의 Precision, Recall, F1을 측정하고 품질 지표를 개선했습니다.

기능 변경 사항에 테스트 자동화를 적용하고 팀원들과 코드 리뷰를 진행했습니다. 리뷰 결과와 API 명세를 업무 문서로 정리하여 공유하고 협업 효율을 개선했습니다.
$project$
        ),
        (
            '팀 프로젝트',
            '통합테스트 업무 자동화팀',
            DATE '2024-12-01',
            DATE '2025-01-15',
            $project$
업무 자동화 프로젝트에서 반복적으로 수행되던 문서 처리 업무를 자동화하기 위해 Python 기반 업무 자동화 기능을 개발했습니다.

자동화 기능을 API로 제공하고 테스트 자동화를 적용하여 입력값과 예외 상황, 기능 변경에 따른 오류를 검증했습니다. 팀원들과 코드 리뷰를 진행하고 개선 결과와 운영 방법을 업무 문서로 작성했습니다.

처음 사용한 FastAPI와 LangChain의 공식 문서를 빠르게 학습한 뒤 기능에 적용하고 팀원들에게 사용 방법을 공유했습니다. 배포 전 체크리스트와 테스트 케이스를 작성하여 API 요청과 예외 상황을 꼼꼼하게 확인했습니다.
$project$
        ),
        (
            '팀 프로젝트',
            '통합테스트 멀티모달 AI 앱팀',
            DATE '2025-01-16',
            DATE '2025-02-28',
            $project$
멀티모달 앱 프로젝트에서 텍스트와 이미지를 함께 입력받아 분석하는 멀티모달 앱을 개발했습니다.

Python과 FastAPI로 백엔드 API를 구현하고 LLM API와 멀티모달 모델을 연동했습니다. Docker로 서비스를 패키징하고 AWS 클라우드 환경에 기능을 배포하여 운영했습니다.

서비스 운영 중 발견한 응답 지연과 실패 요청을 분석하고 캐시와 프롬프트를 개선하여 성능·비용·안정성을 개선했습니다. 개인정보 보호와 보안 기준을 적용하고 정보보호 점검 결과를 문서화했습니다.
$project$
        )
) AS seed(activity_type, organization, start_date, end_date, description)
WHERE u.email = 'ai.test2@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM activities a
      WHERE a.resume_id = r.id
        AND a.organization = seed.organization
        AND a.start_date = seed.start_date
  );

UPDATE cover_letter_items cli
SET answer = CASE q.question_type
        WHEN 'MOTIVATION' THEN $motivation$
생성형 AI 기능을 실제 서비스에서 안정적으로 운영하는 AI 서비스 개발자가 되고 싶어 지원했습니다. RAG AI 챗봇 프로젝트에서 Python과 FastAPI로 백엔드 API를 개발하고 LangChain을 사용하여 RAG 파이프라인과 문서 검색 기능을 구현했습니다. 문서를 Vector DB에 저장하고 검색 결과를 LLM에 전달했으며 OpenAI LLM API로 답변 생성 기능을 구현했습니다.

Docker로 서비스를 컨테이너화하고 AWS 클라우드 환경에 기능을 배포했습니다. 개인정보 보호와 보안 기준을 준수하고 정보보호 의식을 바탕으로 민감정보를 환경변수로 관리했으며, 개인정보 탐지와 보안 필터를 적용했습니다.

추천 서비스, 업무 자동화, 멀티모달 앱 프로젝트를 수행하며 사용자 피드백 기반 제품 개선, 테스트 자동화, 코드 리뷰, 품질 지표 개선을 경험했습니다. 이러한 경험을 바탕으로 품질과 비용, 보안, 운영 안정성을 함께 고려하는 AI 서비스를 개발하고 싶습니다.
$motivation$
        WHEN 'EXPERIENCE' THEN $experience$
RAG AI 챗봇 프로젝트에서 Python, FastAPI, LangChain을 사용하여 AI 챗봇과 문서 검색 기능을 개발했습니다. Docker로 서비스를 패키징하고 AWS 클라우드 환경에 배포한 뒤 로그와 응답 시간을 모니터링했습니다.

추천 서비스 프로젝트에서는 사용자의 스킬과 채용 공고를 비교하는 추천 서비스를 개발했습니다. 사용자 피드백을 분석하여 추천 기준과 제품 기능을 개선하고 Precision, Recall, F1 결과를 바탕으로 품질 지표를 개선했습니다. 기능 변경에는 테스트 자동화를 적용하고 팀원들과 코드 리뷰를 진행했습니다.

업무 자동화 프로젝트에서는 Python으로 문서 처리 업무를 자동화하고 FastAPI 기반 API로 제공했습니다. 멀티모달 앱 프로젝트에서는 텍스트와 이미지를 함께 분석하는 기능을 개발하고 LLM API와 멀티모달 모델을 연동했습니다.

서비스 운영 중 발생한 장애 원인을 분석하고 재발 방지 대책을 업무 문서로 작성했습니다. 반복 임베딩과 긴 프롬프트를 개선하여 성능·비용·안정성을 개선했으며 개인정보 보호와 보안 기준을 준수했습니다.
$experience$
        WHEN 'PERSONALITY' THEN $personality$
문제가 발생하면 로그와 데이터를 확인하고 재현 조건을 정리하여 원인을 단계적으로 좁힙니다. 배포 전 체크리스트와 테스트 케이스로 API 요청과 예외 상황을 꼼꼼하게 검증하고, 장애 원인과 재발 방지 대책을 업무 문서로 남겼습니다.

처음 사용하는 FastAPI와 LangChain은 공식 문서를 빠르게 학습하여 프로젝트에 적용하고 팀원들에게 사용 방법을 공유했습니다. 팀원의 피드백과 코드 리뷰 결과를 반영하여 기능을 수정했으며, 의견과 결정 배경을 공유해 협업 효율을 개선했습니다.

완성도를 높이려다 한 문제를 오래 고민하는 단점을 보완하기 위해 필수 개선 사항과 추후 개선 사항을 나누고 우선순위를 정합니다. 실제 사용자에게 미치는 영향을 먼저 확인하고 사용자 피드백을 제품 개선에 반영하고 있습니다.
$personality$
        ELSE cli.answer
    END,
    updated_at = CURRENT_TIMESTAMP
FROM cover_letters cl
JOIN users u ON u.id = cl.user_id,
     cover_letter_questions q
WHERE cli.cover_letter_id = cl.id
  AND q.id = cli.question_id
  AND u.email = 'ai.test2@example.com'
  AND q.question_type IN ('MOTIVATION', 'EXPERIENCE', 'PERSONALITY');

-- ============================================================================
-- AI 콘텐츠 생성 서비스 개발자 E2E 사용자의 자기소개서입니다.
-- V20260808150000000에서 생성한 ai.test2@example.com 사용자를 이메일로 찾아
-- 동일한 user_id에 자기소개서와 세 개의 기본 문항 답변을 연결합니다.
-- 청크와 임베딩은 스킬 분석 파이프라인에서 생성합니다.
-- ============================================================================

INSERT INTO cover_letters (user_id)
SELECT id
FROM users
WHERE email = 'ai.test2@example.com'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO cover_letter_items (cover_letter_id, question_id, answer)
SELECT
    cl.id,
    q.id,
    provided.answer
FROM cover_letters cl
JOIN users u ON u.id = cl.user_id
CROSS JOIN (
    VALUES
        (
            'MOTIVATION',
            $motivation$
생성형 AI 기능을 단순한 데모 수준이 아니라 실제 서비스에서 안정적으로 동작하도록 구현하는 AI 서비스 개발자가 되고 싶어 지원했습니다. 프로젝트를 진행하며 JavaScript와 TypeScript를 활용해 백엔드 API를 구현했고, OpenAI LLM API를 연동해 사용자의 요청에 따라 콘텐츠를 생성하는 기능을 개발했습니다. 또한 내부 문서를 청킹하고 임베딩한 뒤 Vector DB에 저장하고, 사용자 요청과 관련된 문서를 검색해 LLM에 전달하는 RAG 파이프라인을 구현했습니다.

AI 기능을 개발할 때는 특정 도구를 사용하는 것보다 문제에 맞는 기술을 선택하고 검증하는 과정을 중요하게 생각했습니다. RAG 검색 결과가 충분하지 않을 때는 답변 생성을 제한하고, 검색 결과와 프롬프트를 조정해 생성 품질을 개선했습니다. 프롬프트는 기능별로 분리해 관리하고 변경 전후의 결과를 비교했으며, 골든셋과 Precision, Recall, F1 등의 지표를 활용해 검색 및 생성 결과를 평가했습니다.

이러한 경험을 바탕으로 콘텐츠 생성 환경에서 RAG 파이프라인, LLM API, 백엔드 API, 프롬프트 관리와 평가 기능을 실제 서비스 요구사항에 맞게 구현하고 싶습니다.
$motivation$
        ),
        (
            'EXPERIENCE',
            $experience$
생성형 AI 서비스는 기능 구현뿐 아니라 결과의 품질, 비용, 보안과 운영 안정성까지 함께 고려해야 한다고 생각합니다. 프로젝트에서는 생성 결과의 품질을 확인하기 위해 골든셋 기반 평가를 진행하고, 검색 결과의 Precision과 Recall을 분석해 RAG 검색 성능을 개선했습니다.

LLM API 사용 과정에서는 토큰 사용량과 호출 횟수를 확인하고 불필요한 프롬프트 내용을 제거해 비용을 줄였습니다. 또한 개인정보나 민감한 정보가 LLM에 그대로 전달되지 않도록 입력 단계에서 개인정보를 탐지하고 차단하는 보안 필터를 적용했습니다.

서비스 운영 중에는 API 오류, 응답 시간, LLM 호출 실패와 같은 로그를 확인하고 반복적으로 발생하는 문제를 분석했습니다. 사용자 피드백도 함께 수집해 생성 결과가 기대와 다른 사례를 정리하고, 프롬프트와 RAG 검색 로직 개선에 반영했습니다.

특정 기술을 적용하는 것 자체보다 실제 사용자가 안정적으로 서비스를 이용할 수 있는지를 중요하게 생각하며, 앞으로도 품질과 안정성, 비용, 보안을 함께 고려하는 AI 서비스를 개발하고 싶습니다.
$experience$
        ),
        (
            'PERSONALITY',
            $personality$
저의 장점은 문제가 생겼을 때 감정적으로 판단하기보다 원인을 하나씩 좁혀가는 편이라는 점입니다. 프로젝트에서 기능 오류나 예상하지 못한 결과가 발생했을 때 로그와 데이터를 먼저 확인하고, 어떤 조건에서 문제가 재현되는지 정리한 뒤 해결 방법을 찾으려고 했습니다. 또한 혼자 판단하기보다 팀원의 의견과 피드백을 듣고 더 나은 방법이 있다면 기존 방식을 수정하는 데 부담을 두지 않는 편입니다.

반면 완성도를 높이려는 성향 때문에 하나의 문제를 오래 고민하는 경우가 있습니다. 이전에는 작은 부분까지 충분히 해결한 뒤 다음 작업으로 넘어가려고 했지만, 프로젝트를 진행하면서 모든 문제를 동시에 완벽하게 해결하기보다는 현재 서비스에 가장 중요한 기능을 먼저 완료하는 것이 중요하다는 점을 배웠습니다. 최근에는 문제를 필수 개선 사항과 추후 개선 사항으로 나누고, 우선순위를 정한 뒤 단계적으로 해결하는 방식으로 보완하고 있습니다.

이런 성향을 바탕으로 AI 서비스를 개발할 때도 장애나 실패를 단순한 오류로 끝내지 않고 원인을 분석해 재발 방지 방법을 찾고, 동료의 피드백을 적극적으로 반영하며 안정적인 서비스를 만드는 개발자가 되고 싶습니다.
$personality$
        )
) AS provided(question_type, answer)
JOIN cover_letter_questions q
  ON q.question_type = provided.question_type
WHERE u.email = 'ai.test2@example.com'
ON CONFLICT (cover_letter_id, question_id) DO UPDATE SET
    answer = EXCLUDED.answer,
    updated_at = CURRENT_TIMESTAMP;

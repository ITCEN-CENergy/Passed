-- AI 콘텐츠 개발자 E2E에서 확인한 명백한 표기 차이만 보강합니다.
-- 상위·하위 또는 관련 관계인 보안/개인정보 보호, LLM/LLM API 등은
-- alias로 합치지 않고 skill_relations 검토 대상으로 남깁니다.

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, source)
SELECT s.id, seed.alias, seed.normalized_alias, 'REVIEWED'
FROM (
    VALUES
        ('벡터 데이터베이스', 'TECHNICAL_SKILL', 'Vector DB', 'vectordb'),
        ('벡터 데이터베이스', 'TECHNICAL_SKILL', 'Vector Database', 'vectordatabase'),
        ('Azure', 'TECHNICAL_SKILL', 'Microsoft Azure', 'microsoftazure'),
        ('LLM API', 'TECHNICAL_SKILL', 'OpenAI LLM API', 'openaillmapi'),
        ('LLM API', 'TECHNICAL_SKILL', 'OpenAI LLM API 연동', 'openaillmapi연동'),
        ('RAG', 'TECHNICAL_SKILL', 'RAG 파이프라인', 'rag파이프라인'),
        ('RAG', 'TECHNICAL_SKILL', 'RAG 파이프라인 설계', 'rag파이프라인설계'),
        ('콘텐츠 생성', 'TECHNICAL_SKILL', '콘텐츠 생성 서비스 구현', '콘텐츠생성서비스구현'),
        ('클라우드', 'TECHNICAL_SKILL', '클라우드 컴퓨팅', '클라우드컴퓨팅'),
        ('로그 분석', 'TECHNICAL_SKILL', '서비스 로그 분석', '서비스로그분석'),
        ('프롬프트 엔지니어링', 'TECHNICAL_SKILL', '프롬프트 설계', '프롬프트설계'),
        ('프롬프트 엔지니어링', 'TECHNICAL_SKILL', '프롬프트 최적화', '프롬프트최적화'),
        ('Microsoft Azure AI Engineer Associate', 'CERTIFICATION',
            'Azure AI Engineer Associate', 'azureaiengineerassociate'),
        ('Microsoft Azure AI Engineer Associate', 'CERTIFICATION',
            'Microsoft Certified: Azure AI Engineer Associate',
            'microsoftcertified:azureaiengineerassociate'),
        ('서비스 모니터링', 'EXPERIENCE',
            '서비스 배포 및 모니터링', '서비스배포및모니터링'),
        ('보안 필터 적용', 'EXPERIENCE',
            '개인정보 보안 필터 적용', '개인정보보안필터적용'),
        ('콘텐츠 생성 프로젝트', 'EXPERIENCE',
            'AI 기반 콘텐츠 생성 플랫폼 개발', 'ai기반콘텐츠생성플랫폼개발'),
        ('기술 부채 개선', 'EXPERIENCE', '기술 부채 정의', '기술부채정의'),
        ('장애 원인 분석', 'EXPERIENCE', '장애 원인 추적', '장애원인추적')
) AS seed(skill_name, category, alias, normalized_alias)
JOIN skills s
  ON s.name = seed.skill_name
 AND s.category = seed.category
ON CONFLICT (skill_id, normalized_alias) DO NOTHING;

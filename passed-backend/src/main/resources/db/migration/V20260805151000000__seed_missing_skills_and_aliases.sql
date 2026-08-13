-- Q. 왜 기존 V20260803111425482__seed_skills.sql을 수정하지 않나요?
-- A. 이미 실행된 Flyway 파일을 바꾸면 팀원 DB의 체크섬이 달라집니다. 신규 스킬과
--    별칭은 항상 더 높은 버전의 마이그레이션에서 추가합니다.

INSERT INTO skills (name, category, description)
VALUES
    ('AWS EC2', 'TECHNICAL_SKILL', 'AWS에서 가상 서버 인스턴스를 생성하고 네트워크·스토리지·보안·확장 설정을 운영하는 기술입니다.'),
    ('AWS S3', 'TECHNICAL_SKILL', 'AWS의 객체 스토리지에 파일과 데이터를 저장하고 권한·수명주기·정적 호스팅을 관리하는 기술입니다.'),
    ('AWS RDS', 'TECHNICAL_SKILL', 'AWS의 관리형 관계형 데이터베이스를 구성하고 백업·복제·가용성·성능을 운영하는 기술입니다.'),
    ('TOEIC', 'CERTIFICATION', '영어 듣기와 읽기 능력을 공인 점수로 인증하는 시험 자격입니다.'),
    ('캐싱', 'TECHNICAL_SKILL', '자주 사용하는 데이터나 연산 결과를 임시 저장하여 응답 시간과 원본 시스템 부하를 줄이는 기술입니다.')
ON CONFLICT (name) DO NOTHING;

-- normalized_alias는 AI 파이프라인의 NFKC·casefold·공백/._- 제거 규칙과 같은 값입니다.
INSERT INTO skill_aliases (skill_id, alias, normalized_alias, source)
SELECT s.id, seed.alias, seed.normalized_alias, 'CURATED'
FROM (
    VALUES
        ('백엔드 API 개발', 'EXPERIENCE', 'API 개발', 'api개발'),
        ('Spring Boot', 'TECHNICAL_SKILL', '스프링부트', '스프링부트'),
        ('React', 'TECHNICAL_SKILL', 'React.js', 'reactjs'),
        ('API 설계', 'EXPERIENCE', 'API 규격 합의', 'api규격합의'),
        ('SQL 튜닝', 'EXPERIENCE', 'SQL 실행 계획 분석', 'sql실행계획분석'),
        ('SQL 튜닝', 'EXPERIENCE', '전체 테이블 조회 제거', '전체테이블조회제거'),
        ('성능 최적화', 'EXPERIENCE', 'API 응답 시간 개선', 'api응답시간개선'),
        ('성능 최적화', 'EXPERIENCE', '병목 구간 제거', '병목구간제거'),
        ('일정 관리', 'EXPERIENCE', '개발 일정 관리', '개발일정관리'),
        ('개선 회고', 'EXPERIENCE', '주간 회고', '주간회고'),
        ('장애 원인 분석', 'EXPERIENCE', '장애 원인 조사', '장애원인조사'),
        ('배포', 'EXPERIENCE', '배포 롤백', '배포롤백'),
        ('고객 상담', 'EXPERIENCE', '고객 문의 분류', '고객문의분류'),
        ('장애 대응', 'EXPERIENCE', '서비스 복구', '서비스복구'),
        ('코드 리뷰', 'EXPERIENCE', '코드 리뷰 기준 문서화', '코드리뷰기준문서화'),
        ('협업', 'BEHAVIORAL_TRAIT', '요구사항 조율', '요구사항조율'),
        ('협업', 'BEHAVIORAL_TRAIT', '팀 합의 도출', '팀합의도출'),
        ('협업', 'BEHAVIORAL_TRAIT', '역할 분담', '역할분담'),
        ('협업', 'BEHAVIORAL_TRAIT', '작업 분담', '작업분담'),
        ('의사소통', 'BEHAVIORAL_TRAIT', '의견 경청', '의견경청'),
        ('의사소통', 'BEHAVIORAL_TRAIT', '장애 원인 설명', '장애원인설명'),
        ('의사소통', 'BEHAVIORAL_TRAIT', '진행 상황 공유', '진행상황공유'),
        ('의사소통', 'BEHAVIORAL_TRAIT', '개선 의견 제시', '개선의견제시'),
        ('의사소통', 'BEHAVIORAL_TRAIT', '영어 역할 협의', '영어역할협의'),
        ('책임감', 'BEHAVIORAL_TRAIT', '수정 배포 책임', '수정배포책임'),
        ('책임감', 'BEHAVIORAL_TRAIT', '마감일 준수', '마감일준수'),
        ('우선순위 설정', 'BEHAVIORAL_TRAIT', '개선 우선순위 설정', '개선우선순위설정'),
        ('공감', 'BEHAVIORAL_TRAIT', '팀원 상황 청취', '팀원상황청취'),
        ('업무 자동화', 'TECHNICAL_SKILL', '배포 확인 자동화', '배포확인자동화'),
        ('문서화', 'TECHNICAL_SKILL', '온보딩 문서 작성', '온보딩문서작성'),
        ('페어링', 'EXPERIENCE', '페어 프로그래밍', '페어프로그래밍'),
        ('AWS EC2', 'TECHNICAL_SKILL', 'EC2', 'ec2'),
        ('AWS S3', 'TECHNICAL_SKILL', 'S3', 's3'),
        ('AWS RDS', 'TECHNICAL_SKILL', 'RDS', 'rds'),
        ('캐싱', 'TECHNICAL_SKILL', '캐시 적용', '캐시적용')
) AS seed(skill_name, category, alias, normalized_alias)
JOIN skills s ON s.name = seed.skill_name AND s.category = seed.category
ON CONFLICT (skill_id, normalized_alias) DO NOTHING;

-- 일반 TOEIC 점수는 숫자가 매번 달라지므로 특정 점수 전체를 별칭으로 넣지 않습니다.
INSERT INTO skill_aliases (skill_id, alias, normalized_alias, source)
SELECT id, 'TOEIC', 'toeic', 'CURATED'
FROM skills
WHERE name = 'TOEIC' AND category = 'CERTIFICATION'
ON CONFLICT (skill_id, normalized_alias) DO NOTHING;

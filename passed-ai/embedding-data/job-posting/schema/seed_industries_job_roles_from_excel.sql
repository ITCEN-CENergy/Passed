-- 원본: 산업과_직무_분류_(1).xlsx / 시트1 / A1:L63
-- 산업 21개와 직무 239개(ID 1~239)를 industries, job_roles에 적재한다.
--
-- 동작:
--   1. 산업명 기준으로 industries에 없는 산업만 추가한다.
--   2. 직무 ID 기준으로 job_roles를 INSERT 또는 UPDATE한다.
--   3. 명시적 ID 삽입 뒤 identity 시퀀스를 MAX(id) 다음으로 보정한다.
--
-- 주의:
--   job_roles.id를 엑셀의 "직무 id"와 일치시키는 기준정보 적재 SQL이다.
--   동일한 (industry_id, job_role_name)이 다른 ID로 이미 존재하면
--   uq_job_roles_industry_name 제약에 의해 중단된다.

BEGIN;

CREATE TEMP TABLE tmp_job_role_seed (
    id            bigint       PRIMARY KEY,
    industry_name varchar(255) NOT NULL,
    job_role_name varchar(255) NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_job_role_seed (id, industry_name, job_role_name)
SELECT
    seed.start_id + roles.ordinality - 1,
    seed.industry_name,
    roles.job_role_name
FROM (
    VALUES
        (
            '기획·전략',
            1::bigint,
            ARRAY[
                '경영·비즈니스기획', '웹기획', '마케팅기획', 'PL·PM·PO',
                '컨설턴트', 'CEO·COO·CTO', 'AI기획자', 'AI사업전략'
            ]::text[]
        ),
        (
            '법무·사무·총무',
            9::bigint,
            ARRAY[
                '경영지원', '사무담당자', '총무', '사무보조', '법무담당자',
                '비서', '변호사', '법무사', '변리사', '노무사', 'AI윤리전문가'
            ]::text[]
        ),
        (
            '인사·HR',
            20::bigint,
            ARRAY[
                '인사담당자', 'HRD·HRM', '노무관리자', '잡매니저',
                '헤드헌터', '직업상담사'
            ]::text[]
        ),
        (
            '회계·세무',
            26::bigint,
            ARRAY[
                '회계담당자', '경리', '세무담당자', '재무담당자', '감사',
                'IR·공시', '회계사', '세무사', '관세사'
            ]::text[]
        ),
        (
            '마케팅·광고·MD',
            35::bigint,
            ARRAY[
                'CRM마케터', '온라인마케터', '콘텐츠마케터', '홍보',
                '설문·리서치', 'MD', '카피라이터', '크리에이티브디렉터',
                '채널관리자', '그로스해커'
            ]::text[]
        ),
        (
            '디자인',
            45::bigint,
            ARRAY[
                '그래픽디자이너', '3D디자이너', '제품디자이너', '산업디자이너',
                '광고디자이너', '시각디자이너', '영상디자이너', '웹디자이너',
                'UI·UX디자이너', '패션디자이너', '편집디자이너', '실내디자이너',
                '공간디자이너', '캐릭터디자이너', '환경디자이너',
                '아트디렉터', '일러스트레이터'
            ]::text[]
        ),
        (
            '물류·무역',
            62::bigint,
            ARRAY[
                '물류관리자', '구매관리자', '자재관리자', '유통관리자', '무역사무원'
            ]::text[]
        ),
        (
            '운전·운송·배송',
            67::bigint,
            ARRAY[
                '납품·배송기사', '배달기사', '수행·운전기사', '화물·중장비기사',
                '버스기사', '택시기사', '조종·기관사'
            ]::text[]
        ),
        (
            '영업',
            74::bigint,
            ARRAY[
                '제품영업', '서비스영업', '해외영업', '광고영업', '금융영업',
                '법인영업', 'IT·기술영업', '영업관리', '영업지원'
            ]::text[]
        ),
        (
            '고객상담·TM',
            83::bigint,
            ARRAY['인바운드상담원', '아웃바운드상담원', '고객센터관리자']::text[]
        ),
        (
            '금융·보험',
            86::bigint,
            ARRAY[
                '금융사무', '보험설계사', '손해사정사', '심사', '은행원·텔러',
                '계리사', '펀드매니저', '애널리스트'
            ]::text[]
        ),
        (
            '식·음료',
            94::bigint,
            ARRAY[
                '요리사', '조리사', '제과제빵사', '바리스타', '셰프·주방장',
                '카페·레스토랑매니저', '홀서버', '주방보조',
                '소믈리에·바텐더', '영양사', '식품연구원', '푸드스타일리스트'
            ]::text[]
        ),
        (
            '고객서비스·리테일',
            106::bigint,
            ARRAY[
                '설치·수리기사', '정비기사', '호텔종사자', '여행에이전트',
                '매장관리자', '뷰티·미용사', '애견미용·훈련',
                '안내데스크·리셉셔니스트', '경호·경비', '운영보조·매니저',
                '이벤트·웨딩플래너', '주차·주유원', '스타일리스트',
                '장례지도사', '가사도우미', '승무원', '플로리스트'
            ]::text[]
        ),
        (
            '엔지니어링·설계',
            123::bigint,
            ARRAY[
                '전기·전자엔지니어', '기계엔지니어', '설계엔지니어',
                '설비엔지니어', '반도체엔지니어', '화학엔지니어',
                '공정엔지니어', '하드웨어엔지니어', '통신엔지니어',
                'RF엔지니어', '필드엔지니어', 'R&D·연구원', 'AI로봇엔지니어'
            ]::text[]
        ),
        (
            '제조·생산',
            136::bigint,
            ARRAY[
                '생산직종사자', '생산·공정관리자', '품질관리자',
                '포장·가공담당자', '공장관리자', '용접사'
            ]::text[]
        ),
        (
            '교육',
            142::bigint,
            ARRAY[
                '유치원·보육교사', '학교·특수학교교사', '대학교수·강사',
                '학원강사', '외국어강사', '기술·전문강사', '학습지·방문교사',
                '학원상담·운영', '교직원·조교', '교재개발·교수설계',
                'AI교육컨설턴트'
            ]::text[]
        ),
        (
            '건축·시설',
            153::bigint,
            ARRAY[
                '건축가', '건축기사', '시공기사', '전기기사', '토목기사',
                '시설관리자', '현장관리자', '안전관리자', '공무', '소방설비',
                '현장보조', '감리원', '도시·조경설계', '환경기사',
                '비파괴검사원', '공인중개사', '감정평가사', '분양매니저'
            ]::text[]
        ),
        (
            '의료·바이오',
            171::bigint,
            ARRAY[
                '의사', '한의사', '간호사', '간호조무사', '약사·한약사',
                '의료기사', '수의사', '수의테크니션', '병원코디네이터',
                '원무행정', '기타의료종사자', '의료·약무보조',
                '바이오·제약연구원', '임상연구원'
            ]::text[]
        ),
        (
            '미디어·문화·스포츠',
            185::bigint,
            ARRAY[
                'PD·감독', '포토그래퍼', '영상편집자', '사운드엔지니어',
                '스태프', '출판·편집', '배급·제작자', '콘텐츠에디터',
                '크리에이터', '기자', '작가', '아나운서', '리포터·성우',
                'MC·쇼호스트', '모델', '연예인·매니저', '인플루언서',
                '통번역사', '큐레이터', '음반기획', '스포츠강사',
                'AI콘텐츠크리에이터'
            ]::text[]
        ),
        (
            '공공·복지',
            207::bigint,
            ARRAY[
                '사회복지사', '요양보호사', '환경미화원', '보건관리자',
                '사서', '자원봉사자', '방역·방재기사'
            ]::text[]
        ),
        (
            'AI·개발·데이터',
            214::bigint,
            ARRAY[
                '백엔드개발자', '프론트엔드개발자', '웹개발자', '앱개발자',
                '시스템엔지니어', '네트워크엔지니어', 'DBA', '데이터엔지니어',
                '데이터사이언티스트', '보안엔지니어', '소프트웨어개발자',
                '게임개발자', '하드웨어개발자', 'AI/ML엔지니어',
                '블록체인개발자', '클라우드엔지니어', '웹퍼블리셔',
                'IT컨설팅', 'QA', 'AI/ML연구원', '데이터분석가',
                '데이터라벨러', '프롬프트엔지니어', 'AI보안전문가',
                'MLOps엔지니어', 'AI서비스개발자'
            ]::text[]
        )
) AS seed(industry_name, start_id, role_names)
CROSS JOIN LATERAL unnest(seed.role_names)
    WITH ORDINALITY AS roles(job_role_name, ordinality);

-- 엑셀에 있는 산업 21개를 이름 기준으로 준비한다.
INSERT INTO industries (industry_name)
SELECT DISTINCT industry_name
FROM tmp_job_role_seed
ON CONFLICT (industry_name) DO NOTHING;

-- 엑셀의 직무 ID를 기준으로 실제 산업·직무명으로 추가 또는 갱신한다.
INSERT INTO job_roles (
    id,
    industry_id,
    job_role_name
)
SELECT
    seed.id,
    industry.id,
    seed.job_role_name
FROM tmp_job_role_seed AS seed
JOIN industries AS industry
  ON industry.industry_name = seed.industry_name
ORDER BY seed.id
ON CONFLICT (id) DO UPDATE SET
    industry_id = EXCLUDED.industry_id,
    job_role_name = EXCLUDED.job_role_name;

-- 명시적 ID 삽입 뒤 자동 생성 시퀀스를 현재 MAX(id) 다음으로 맞춘다.
SELECT setval(
    pg_get_serial_sequence('industries', 'id'),
    COALESCE((SELECT MAX(id) FROM industries), 0) + 1,
    false
);

SELECT setval(
    pg_get_serial_sequence('job_roles', 'id'),
    COALESCE((SELECT MAX(id) FROM job_roles), 0) + 1,
    false
);

-- 커밋 전에 원본과 DB가 일치하는지 검증한다.
DO $$
DECLARE
    seed_count     integer;
    matched_count  integer;
BEGIN
    SELECT COUNT(*) INTO seed_count
    FROM tmp_job_role_seed;

    SELECT COUNT(*) INTO matched_count
    FROM tmp_job_role_seed AS seed
    JOIN industries AS industry
      ON industry.industry_name = seed.industry_name
    JOIN job_roles AS role
      ON role.id = seed.id
     AND role.industry_id = industry.id
     AND role.job_role_name = seed.job_role_name;

    IF seed_count <> 239 OR matched_count <> seed_count THEN
        RAISE EXCEPTION
            '산업·직무 적재 검증 실패: seed_count=%, matched_count=%',
            seed_count,
            matched_count;
    END IF;
END;
$$;

COMMIT;

-- 실행 결과 확인
SELECT
    COUNT(*) FILTER (WHERE id BETWEEN 1 AND 239) AS job_role_count_1_239,
    MIN(id) FILTER (WHERE id BETWEEN 1 AND 239) AS min_job_role_id,
    MAX(id) FILTER (WHERE id BETWEEN 1 AND 239) AS max_job_role_id
FROM job_roles;

-- 정상 결과:
-- job_role_count_1_239 = 239
-- min_job_role_id      = 1
-- max_job_role_id      = 239

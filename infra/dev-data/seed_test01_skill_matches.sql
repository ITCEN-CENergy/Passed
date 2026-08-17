-- Local demo data for test1234@test.com.
-- Builds three completed single-posting matches from the real posting-skill data.

DO $$
DECLARE
    target_user_id bigint;
    active_policy_id bigint;
    target_posting_id bigint;
    target_rank integer;
    inserted_run_id bigint;
    inserted_recommendation_id bigint;
    snapshot jsonb;
    important_count integer;
    required_count integer;
    required_owned integer;
    important_matched integer;
    required_coverage numeric(5, 4);
    required_level_rate numeric(5, 4);
    required_score numeric(7, 4);
    preferred_score numeric(7, 4);
    related_score numeric(7, 4);
    important_bonus numeric(7, 4);
    total_score numeric(7, 4);
    grade varchar(30);
    reason_text text;
BEGIN
    SELECT id INTO target_user_id
    FROM users
    WHERE email = 'test1234@test.com';

    IF target_user_id IS NULL THEN
        RAISE NOTICE 'Skipping demo matches: test1234@test.com does not exist.';
        RETURN;
    END IF;

    SELECT id INTO active_policy_id
    FROM recommendation_scoring_policies
    WHERE policy_code = 'SKILL_MATCH' AND status = 'ACTIVE'
    ORDER BY activated_at DESC NULLS LAST, id DESC
    LIMIT 1;

    IF active_policy_id IS NULL THEN
        RAISE EXCEPTION 'Active SKILL_MATCH policy not found.';
    END IF;

    -- Use the required/preferred skills of one representative software posting as
    -- the demo user's profile. Existing user-entered skills are preserved.
    INSERT INTO user_skills (user_id, skill_id, skill_level, is_important)
    SELECT
        target_user_id,
        posting_skill.skill_id,
        CASE WHEN skill.category = 'CERTIFICATION' THEN 1 ELSE posting_skill.skill_level END,
        skill.name IN ('Java', 'Docker', 'SQL')
    FROM job_posting_skills posting_skill
    JOIN skills skill ON skill.id = posting_skill.skill_id
    WHERE posting_skill.job_posting_id = 3184
      AND posting_skill.skill_type IN ('REQUIRED', 'PREFERRED')
    ON CONFLICT (user_id, skill_id) DO NOTHING;

    SELECT jsonb_build_object(
        'schemaVersion', 1,
        'skills', COALESCE(
            jsonb_agg(
                jsonb_build_object(
                    'skillId', skill_id,
                    'skillLevel', skill_level,
                    'isImportant', is_important
                ) ORDER BY skill_id
            ),
            '[]'::jsonb
        )
    ), COUNT(*) FILTER (WHERE is_important)
    INTO snapshot, important_count
    FROM user_skills
    WHERE user_id = target_user_id;

    CREATE TEMP TABLE demo_match_details ON COMMIT DROP AS
    WITH target_postings(job_posting_id, rank_order) AS (
        VALUES (3184::bigint, 1), (3208::bigint, 2), (3136::bigint, 3)
    ), raw AS (
        SELECT
            target.job_posting_id,
            target.rank_order,
            posting_skill.skill_id,
            posting_skill.skill_type,
            posting_skill.skill_level AS required_level,
            user_skill.skill_level AS user_level,
            CASE WHEN skill.category = 'CERTIFICATION' THEN 'OWNERSHIP' ELSE 'LEVEL' END AS evaluation_type,
            user_skill.id IS NOT NULL AS is_owned,
            COALESCE(user_skill.is_important, FALSE) AS is_user_important,
            CASE
                WHEN user_skill.id IS NULL THEN 0::numeric
                WHEN skill.category = 'CERTIFICATION' THEN 1::numeric
                ELSE LEAST(user_skill.skill_level::numeric / posting_skill.skill_level, 1::numeric)
            END AS match_rate,
            COUNT(*) OVER (PARTITION BY target.job_posting_id, posting_skill.skill_type) AS type_count
        FROM target_postings target
        JOIN job_posting_skills posting_skill
          ON posting_skill.job_posting_id = target.job_posting_id
        JOIN skills skill ON skill.id = posting_skill.skill_id
        LEFT JOIN user_skills user_skill
          ON user_skill.user_id = target_user_id
         AND user_skill.skill_id = posting_skill.skill_id
    )
    SELECT
        raw.*,
        raw.is_owned AND raw.match_rate >= 1 AS is_requirement_satisfied,
        trunc((CASE raw.skill_type
            WHEN 'REQUIRED' THEN 60::numeric
            WHEN 'PREFERRED' THEN 20::numeric
            ELSE 10::numeric
        END) / raw.type_count, 4) AS base_max_score
    FROM raw;

    FOR target_posting_id, target_rank IN
        SELECT DISTINCT job_posting_id, rank_order
        FROM demo_match_details
        ORDER BY rank_order
    LOOP
        IF EXISTS (
            SELECT 1
            FROM job_recommendations recommendation
            JOIN recommendation_runs run ON run.id = recommendation.recommendation_run_id
            WHERE run.user_id = target_user_id
              AND run.recommendation_type = 'SINGLE_POSTING'
              AND run.status = 'COMPLETED'
              AND recommendation.job_posting_id = target_posting_id
        ) THEN
            CONTINUE;
        END IF;

        SELECT
            COUNT(*) FILTER (WHERE skill_type = 'REQUIRED'),
            COUNT(*) FILTER (WHERE skill_type = 'REQUIRED' AND is_owned),
            COUNT(*) FILTER (WHERE is_user_important AND is_owned),
            COALESCE(
                COUNT(*) FILTER (WHERE skill_type = 'REQUIRED' AND is_owned)::numeric
                / NULLIF(COUNT(*) FILTER (WHERE skill_type = 'REQUIRED'), 0),
                0
            ),
            COALESCE(
                AVG(match_rate) FILTER (WHERE skill_type = 'REQUIRED'),
                0
            ),
            COALESCE(SUM(trunc(base_max_score * match_rate, 4)) FILTER (WHERE skill_type = 'REQUIRED'), 0),
            COALESCE(SUM(trunc(base_max_score * match_rate, 4)) FILTER (WHERE skill_type = 'PREFERRED'), 0),
            COALESCE(SUM(trunc(base_max_score * match_rate, 4)) FILTER (WHERE skill_type = 'RELATED'), 0),
            COALESCE(SUM(
                CASE WHEN is_user_important THEN trunc(
                    trunc(10::numeric / NULLIF(important_count, 0), 12)
                    * match_rate
                    * CASE skill_type WHEN 'REQUIRED' THEN 1 WHEN 'PREFERRED' THEN 0.7 ELSE 0.4 END,
                    4
                ) ELSE 0 END
            ), 0)
        INTO
            required_count, required_owned, important_matched,
            required_coverage, required_level_rate,
            required_score, preferred_score, related_score, important_bonus
        FROM demo_match_details
        WHERE job_posting_id = target_posting_id;

        required_coverage := round(required_coverage, 4);
        required_level_rate := round(required_level_rate, 4);
        total_score := required_score + preferred_score + related_score + important_bonus;

        grade := CASE
            WHEN total_score >= 80 AND required_coverage >= 0.8
                 AND required_level_rate >= 0.8 AND important_matched >= 1
                THEN 'HIGHLY_RECOMMENDED'
            WHEN total_score >= 65 AND required_coverage >= 0.6 THEN 'RECOMMENDED'
            WHEN total_score >= 50 AND required_coverage >= 0.4 THEN 'CHALLENGING'
            ELSE 'LOW_MATCH'
        END;

        reason_text := CASE target_rank
            WHEN 1 THEN 'Java, SQL, Docker 등 핵심 기술과 실무 경험이 공고의 필수 역량에 매우 잘 맞습니다.'
            WHEN 2 THEN '백엔드 개발의 핵심 역량을 다수 보유해 빠르게 업무에 기여할 수 있습니다.'
            ELSE '주요 개발 역량은 잘 맞으며, 일부 부족한 기술을 보완하면 경쟁력이 더 높아집니다.'
        END;

        INSERT INTO recommendation_runs (
            user_id, status, scoring_policy_id, user_skill_snapshot_hash,
            user_skill_snapshot, preference_snapshot, started_at, completed_at,
            candidate_posting_count, required_qualified_posting_count,
            recommendation_type
        ) VALUES (
            target_user_id, 'COMPLETED', active_policy_id, repeat('d', 64),
            snapshot, '{}'::jsonb,
            CURRENT_TIMESTAMP - make_interval(days => 4 - target_rank),
            CURRENT_TIMESTAMP - make_interval(days => 4 - target_rank) + interval '8 seconds',
            1, CASE WHEN required_coverage >= 0.5 THEN 1 ELSE 0 END,
            'SINGLE_POSTING'
        ) RETURNING id INTO inserted_run_id;

        INSERT INTO job_recommendations (
            recommendation_run_id, job_posting_id, total_score,
            required_score, preferred_score, related_score, important_skill_bonus,
            required_skill_count, required_owned_count, required_coverage_rate,
            required_level_match_rate, important_skill_count, important_match_count,
            candidate_tier, recommendation_grade, rank_order, reason,
            strengths, weaknesses
        ) VALUES (
            inserted_run_id, target_posting_id, total_score,
            required_score, preferred_score, related_score, important_bonus,
            required_count, required_owned, required_coverage,
            required_level_rate, important_count, important_matched,
            CASE WHEN important_count > 0 AND important_matched >= 1 THEN 'PRIMARY' ELSE 'FALLBACK' END,
            grade, 1, reason_text,
            '보유한 핵심 개발 기술과 프로젝트 경험이 공고의 요구사항에 부합합니다.',
            '미보유 스킬은 지원 전 실습 프로젝트로 보완하는 것을 권장합니다.'
        ) RETURNING id INTO inserted_recommendation_id;

        INSERT INTO job_recommendation_skill_details (
            job_recommendation_id, skill_id, skill_type, required_level,
            user_level, evaluation_type, is_owned, is_requirement_satisfied,
            is_user_important, match_rate, base_max_score,
            base_contribution_score, important_bonus_contribution_score
        )
        SELECT
            inserted_recommendation_id, skill_id, skill_type, required_level,
            user_level, evaluation_type, is_owned, is_requirement_satisfied,
            is_user_important, round(match_rate, 4), base_max_score,
            trunc(base_max_score * match_rate, 4),
            CASE WHEN is_user_important THEN trunc(
                trunc(10::numeric / NULLIF(important_count, 0), 12)
                * match_rate
                * CASE skill_type WHEN 'REQUIRED' THEN 1 WHEN 'PREFERRED' THEN 0.7 ELSE 0.4 END,
                4
            ) ELSE 0 END
        FROM demo_match_details
        WHERE job_posting_id = target_posting_id
        ORDER BY skill_id;
    END LOOP;
END;
$$;

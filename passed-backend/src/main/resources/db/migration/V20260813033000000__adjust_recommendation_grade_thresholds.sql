-- SKILL_MATCH v1의 등급 구간을 완화한다.
-- LOW_MATCH를 완전한 fallback으로 두어 단일 공고 추천이 항상 등급을 받도록 한다.
WITH grade_thresholds (
    recommendation_grade,
    min_total_score,
    min_required_coverage_rate
) AS (
    VALUES
        ('HIGHLY_RECOMMENDED', 80.00::numeric, 0.8000::numeric),
        ('RECOMMENDED',        65.00::numeric, 0.6000::numeric),
        ('CHALLENGING',        50.00::numeric, 0.4000::numeric),
        ('LOW_MATCH',           0.00::numeric, 0.0000::numeric)
)
UPDATE recommendation_grade_rules AS grade_rule
SET min_total_score = threshold.min_total_score,
    min_required_coverage_rate = threshold.min_required_coverage_rate
FROM recommendation_scoring_policies AS policy,
     grade_thresholds AS threshold
WHERE grade_rule.scoring_policy_id = policy.id
  AND grade_rule.recommendation_grade = threshold.recommendation_grade
  AND policy.policy_code = 'SKILL_MATCH'
  AND policy.version = 'v1';

DO $$
DECLARE
    matched_rule_count integer;
BEGIN
    SELECT COUNT(*)
    INTO matched_rule_count
    FROM recommendation_grade_rules AS grade_rule
    JOIN recommendation_scoring_policies AS policy
      ON policy.id = grade_rule.scoring_policy_id
    WHERE policy.policy_code = 'SKILL_MATCH'
      AND policy.version = 'v1'
      AND (
          (grade_rule.recommendation_grade = 'HIGHLY_RECOMMENDED'
              AND grade_rule.min_total_score = 80.00
              AND grade_rule.min_required_coverage_rate = 0.8000)
          OR (grade_rule.recommendation_grade = 'RECOMMENDED'
              AND grade_rule.min_total_score = 65.00
              AND grade_rule.min_required_coverage_rate = 0.6000)
          OR (grade_rule.recommendation_grade = 'CHALLENGING'
              AND grade_rule.min_total_score = 50.00
              AND grade_rule.min_required_coverage_rate = 0.4000)
          OR (grade_rule.recommendation_grade = 'LOW_MATCH'
              AND grade_rule.min_total_score = 0.00
              AND grade_rule.min_required_coverage_rate = 0.0000)
      );

    IF matched_rule_count <> 4 THEN
        RAISE EXCEPTION
            'Expected 4 updated SKILL_MATCH v1 grade rules, found %',
            matched_rule_count;
    END IF;
END;
$$;

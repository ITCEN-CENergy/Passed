-- Certification competencies represent possession (1), not the general 1-3 proficiency scale.
-- Correct the hard-coded development data for user 178.
UPDATE user_skills us
SET skill_level = 1,
    updated_at = CURRENT_TIMESTAMP
FROM skills s
WHERE us.skill_id = s.id
  AND us.user_id = 178
  AND s.category = 'CERTIFICATION'
  AND us.skill_level <> 1;

-- Roadmap generation reads the persisted recommendation detail, so also normalize
-- recommendation results that were created before this correction was applied.
UPDATE job_recommendation_skill_details d
SET user_level = 1
FROM job_recommendations jr
JOIN recommendation_runs rr ON rr.id = jr.recommendation_run_id,
     skills s
WHERE d.job_recommendation_id = jr.id
  AND d.skill_id = s.id
  AND rr.user_id = 178
  AND s.category = 'CERTIFICATION'
  AND d.user_level IS NOT NULL
  AND d.user_level <> 1;

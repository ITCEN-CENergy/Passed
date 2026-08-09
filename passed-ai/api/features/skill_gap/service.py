from __future__ import annotations

from resume_pipeline.db import connection

from .schema import LearningCompetencyItem, LearningCompetencyResponse


class SkillGapResourceNotFoundError(LookupError):
    """The requested user or job posting does not exist."""


_COMPETENCY_SQL = """
    SELECT
        skill.id AS standard_competency_id,
        skill.name AS standard_competency_name,
        skill.category,
        posting_skill.skill_type AS requirement_type,
        CASE
            -- A satisfied important skill is returned as a reinforcement target.
            -- The roadmap contract requires currentLevel <= targetLevel, so a
            -- user level above the posting target is normalized to that target.
            WHEN user_skill.id IS NOT NULL THEN LEAST(
                user_skill.skill_level,
                posting_skill.skill_level
            )
            WHEN skill.category = 'CERTIFICATION' THEN 0
            ELSE 1
        END AS current_level,
        posting_skill.skill_level AS target_level,
        evidence.evidence_text AS current_level_evidence
    FROM job_posting_skills posting_skill
    JOIN skills skill ON skill.id = posting_skill.skill_id
    LEFT JOIN user_skills user_skill
        ON user_skill.skill_id = posting_skill.skill_id
       AND user_skill.user_id = %s
    LEFT JOIN LATERAL (
        SELECT user_evidence.evidence_text
        FROM user_skill_evidences user_evidence
        WHERE user_evidence.user_skill_id = user_skill.id
        ORDER BY user_evidence.created_at DESC, user_evidence.id DESC
        LIMIT 1
    ) evidence ON TRUE
    WHERE posting_skill.job_posting_id = %s
      AND skill.category IS NOT NULL
      AND (
          user_skill.id IS NULL
          OR user_skill.skill_level < posting_skill.skill_level
          OR user_skill.is_important = TRUE
      )
    ORDER BY posting_skill.skill_type, skill.id
"""


def _exists(conn: object, table: str, resource_id: int) -> bool:
    # table is selected only by this module's fixed call sites.
    with conn.cursor() as cur:  # type: ignore[attr-defined]
        cur.execute(f"SELECT 1 FROM {table} WHERE id = %s", (resource_id,))
        return cur.fetchone() is not None


def analyze_learning_competencies(
    user_id: int,
    job_posting_id: int,
) -> LearningCompetencyResponse:
    """Return every insufficient skill and each satisfied important user skill."""
    with connection() as conn:
        if not _exists(conn, "users", user_id):
            raise SkillGapResourceNotFoundError(f"user not found: {user_id}")
        if not _exists(conn, "job_postings", job_posting_id):
            raise SkillGapResourceNotFoundError(
                f"job posting not found: {job_posting_id}"
            )

        with conn.cursor() as cur:
            cur.execute(_COMPETENCY_SQL, (user_id, job_posting_id))
            rows = cur.fetchall()

    return LearningCompetencyResponse(
        user_id=user_id,
        job_posting_id=job_posting_id,
        competencies=[LearningCompetencyItem(**row) for row in rows],
    )

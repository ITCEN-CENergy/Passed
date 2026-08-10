from __future__ import annotations

import asyncio
import re

from resume_pipeline.db import connection

from .schema import Competency


def _normalize(value: object, maximum_length: int) -> str:
    text = re.sub(r"\s+", " ", str(value or "")).strip()
    return text[:maximum_length].rstrip()


def _load_search_context(
    skill_ids: list[int], job_posting_ids: list[int]
) -> tuple[dict[int, str], dict[int, str]]:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT id, description FROM skills WHERE id = ANY(%s)",
                (skill_ids,),
            )
            skill_descriptions = {
                int(row["id"]): _normalize(row["description"], 180)
                for row in cur.fetchall()
            }
            cur.execute(
                """
                SELECT id, title, position_detail, main_duty
                FROM job_postings
                WHERE id = ANY(%s)
                """,
                (job_posting_ids,),
            )
            posting_contexts = {
                int(row["id"]): _normalize(
                    " ".join(filter(None, (
                        row["title"], row["position_detail"], row["main_duty"]
                    ))),
                    260,
                )
                for row in cur.fetchall()
            }
    return skill_descriptions, posting_contexts


def _build_queries_sync(competencies: list[Competency]) -> dict[str, str]:
    job_posting_ids = sorted({
        source.jobPostingId
        for competency in competencies
        for source in competency.sources
    })
    skill_descriptions, posting_contexts = _load_search_context(
        [competency.standardCompetencyId for competency in competencies],
        job_posting_ids,
    )
    queries: dict[str, str] = {}
    for competency in competencies:
        posting_context = " ".join(
            posting_contexts.get(source.jobPostingId, "")
            for source in competency.sources
        )
        queries[competency.roadmapSkillKey] = _normalize(" ".join(filter(None, (
            competency.standardCompetencyName,
            competency.category.value.replace("_", " "),
            skill_descriptions.get(competency.standardCompetencyId, ""),
            posting_context,
            "학습 가이드 실무 실습",
        ))), 500)
    return queries


async def build_contextual_search_queries(
    competencies: list[Competency],
) -> dict[str, str]:
    try:
        return await asyncio.to_thread(_build_queries_sync, competencies)
    except Exception:
        return {
            competency.roadmapSkillKey: (
                f"{competency.standardCompetencyName} "
                f"{competency.category.value.replace('_', ' ')} 학습 가이드 실무 실습"
            )
            for competency in competencies
        }

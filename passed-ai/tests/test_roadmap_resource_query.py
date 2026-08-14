import pytest

from api.features.roadmap.resource_query import (
    build_competency_search_profiles,
    load_curated_search_profiles,
)
from api.features.roadmap.schema import Competency, CompetencySource


def _competency(
    skill_id: int = 323,
    name: str = "문서 검색",
) -> Competency:
    return Competency(
        roadmapSkillKey="document-search",
        standardCompetencyId=skill_id,
        standardCompetencyName=name,
        category="TECHNICAL_SKILL",
        currentLevel=1,
        targetLevel=2,
        requirementType="REQUIRED",
        gapLevel=1,
        frequency=1,
        priority=1,
        sources=[CompetencySource(jobPostingId=4730)],
    )


def test_search_profile_files_cover_every_database_skill() -> None:
    profiles = load_curated_search_profiles()

    assert len(profiles) == 1655
    assert all(profile["queries"]["ko"] for profile in profiles.values())
    assert profiles[323]["skillName"] == "문서 검색"
    assert "BM25 information retrieval tutorial" in profiles[323]["queries"]["en"]
    assert profiles[12]["reviewed"] is True
    assert profiles[694]["reviewed"] is True
    assert profiles[699]["reviewed"] is True


@pytest.mark.asyncio
async def test_search_profile_uses_json_queries_and_exclusions() -> None:
    profile = (await build_competency_search_profiles([_competency()]))[
        "document-search"
    ]

    assert "문서 정보 검색 색인 실습" in profile.context
    assert "BM25 information retrieval tutorial" in profile.context
    assert "davinci resolve" in profile.excluded_terms
    assert "bm25" in profile.distinctive_terms


@pytest.mark.asyncio
async def test_missing_search_profile_fails_fast() -> None:
    with pytest.raises(ValueError, match="missing JSON search profile"):
        await build_competency_search_profiles([
            _competency(skill_id=999999, name="없는 역량")
        ])

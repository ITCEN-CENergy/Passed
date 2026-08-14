from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path

from api.features.roadmap.schema import Competency


@dataclass(frozen=True)
class CompetencySearchProfile:
    context: str
    distinctive_terms: tuple[str, ...]
    excluded_terms: tuple[str, ...] = ()


_SEARCH_PROFILE_DIRECTORY = Path(__file__).with_name("data") / "skill_search_profiles"
_PROFILE_STOP_WORDS = {
    "개발", "관리", "관련", "결과", "기능", "기술", "데이터", "문서", "사용",
    "서비스", "실무", "역량", "정보", "학습", "활용", "application", "development",
}


def _profile_tokens(value: str) -> set[str]:
    return {
        token
        for token in re.findall(r"[가-힣A-Za-z0-9+#.]+", value.casefold())
        if len(token) >= 2 and token not in _PROFILE_STOP_WORDS
    }


def load_curated_search_profiles() -> dict[int, dict[str, object]]:
    profiles: dict[int, dict[str, object]] = {}
    for path in sorted(_SEARCH_PROFILE_DIRECTORY.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(payload, dict):
            raise ValueError(f"search profile file must contain an object: {path}")
        for raw_skill_id, item in payload.items():
            skill_id = int(raw_skill_id)
            if skill_id in profiles:
                raise ValueError(f"duplicate search profile skill id: {skill_id}")
            if not isinstance(item, dict) or not item.get("skillName"):
                raise ValueError(f"invalid search profile: {skill_id}")
            queries = item.get("queries")
            if not isinstance(queries, dict):
                raise ValueError(f"invalid search profile queries: {skill_id}")
            profiles[skill_id] = item
    return profiles


async def build_competency_search_profiles(
    competencies: list[Competency],
) -> dict[str, CompetencySearchProfile]:
    stored_profiles = load_curated_search_profiles()
    result: dict[str, CompetencySearchProfile] = {}
    for competency in competencies:
        skill_id = competency.standardCompetencyId
        stored = stored_profiles.get(skill_id)
        if stored is None:
            raise ValueError(f"missing JSON search profile for skill id: {skill_id}")
        queries = stored["queries"]
        search_terms = tuple(
            str(term).strip()
            for language in ("ko", "en")
            for term in queries.get(language, [])
            if str(term).strip()
        )
        if not search_terms:
            raise ValueError(f"empty JSON search profile for skill id: {skill_id}")
        context = " ".join(search_terms)
        excluded_terms = tuple(
            str(term).casefold().strip()
            for term in stored.get("excludeTerms", [])
            if str(term).strip()
        )
        result[competency.roadmapSkillKey] = CompetencySearchProfile(
            context=context,
            distinctive_terms=tuple(sorted(_profile_tokens(context)))[:12],
            excluded_terms=excluded_terms,
        )
    return result

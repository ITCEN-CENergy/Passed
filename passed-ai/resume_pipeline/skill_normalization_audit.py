"""현재 aggressive 정규화와 제안한 conservative 정규화의 읽기 전용 비교."""

from __future__ import annotations

from collections import defaultdict
from collections.abc import Callable

from pydantic import BaseModel, Field

from .skill_mapping_models import SkillMappingGoldenCase
from .skill_mapping_worker import (
    SkillAlias,
    SkillMaster,
    load_skill_aliases,
    load_skill_masters,
    normalize_alias_candidate,
    normalize_alias_candidate_conservative,
    normalize_skill_name,
    normalize_skill_name_conservative,
)


class StrategyAudit(BaseModel):
    name: str
    exact_resolved: int = 0
    normalized_master_resolved: int = 0
    alias_resolved: int = 0
    ambiguous: int = 0
    unresolved: int = 0
    collision_keys: int = 0


class NormalizationAuditReport(BaseModel):
    master_count: int
    alias_count: int
    stored_alias_mismatch_current: int
    stored_alias_mismatch_conservative: int
    current: StrategyAudit
    conservative: StrategyAudit
    conservative_collision_samples: dict[str, list[str]] = Field(default_factory=dict)


def _collisions(
    masters: list[SkillMaster],
    aliases: list[SkillAlias],
    normalize: Callable[[str], str],
    normalize_alias: Callable[[str, object], str],
) -> dict[str, list[str]]:
    owners: dict[tuple[str, str], set[str]] = defaultdict(set)
    for master in masters:
        owners[(master.category.value, normalize(master.name))].add(master.name)
    for alias in aliases:
        owners[
            (alias.category.value, normalize_alias(alias.alias, alias.category))
        ].add(alias.skill_name)
    return {
        f"{category}:{key}": sorted(skill_names)
        for (category, key), skill_names in owners.items()
        if len(skill_names) > 1
    }


def _strategy_audit(
    name: str,
    cases: list[SkillMappingGoldenCase],
    masters: list[SkillMaster],
    aliases: list[SkillAlias],
    normalize: Callable[[str], str],
    normalize_alias: Callable[[str, object], str],
) -> StrategyAudit:
    counts = StrategyAudit(name=name)
    for case in cases:
        same_masters = [
            item for item in masters if item.category is case.extracted_category
        ]
        exact = [item for item in same_masters if item.name == case.extracted_name]
        if len(exact) == 1:
            counts.exact_resolved += 1
            continue

        key = normalize(case.extracted_name)
        normalized = [item for item in same_masters if normalize(item.name) == key]
        if len(normalized) == 1:
            counts.normalized_master_resolved += 1
            continue
        if len(normalized) > 1:
            counts.ambiguous += 1
            continue

        alias_matches = [
            item
            for item in aliases
            if item.category is case.extracted_category
            and normalize_alias(item.alias, item.category)
            == normalize_alias(case.extracted_name, case.extracted_category)
        ]
        alias_skill_ids = {item.skill_id for item in alias_matches}
        if len(alias_skill_ids) == 1:
            counts.alias_resolved += 1
        elif alias_skill_ids:
            counts.ambiguous += 1
        else:
            counts.unresolved += 1
    counts.collision_keys = len(
        _collisions(masters, aliases, normalize, normalize_alias)
    )
    return counts


def audit_normalization(
    conn: object,
    cases: list[SkillMappingGoldenCase],
) -> NormalizationAuditReport:
    masters = load_skill_masters(conn)
    aliases = load_skill_aliases(conn)
    with conn.cursor() as cur:  # type: ignore[attr-defined]
        cur.execute(
            "SELECT alias, normalized_alias FROM skill_aliases "
            "WHERE is_active = TRUE ORDER BY id"
        )
        stored_aliases = cur.fetchall()

    pairs = [
        (
            str(row["alias"] if isinstance(row, dict) else row[0]),
            str(row["normalized_alias"] if isinstance(row, dict) else row[1]),
        )
        for row in stored_aliases
    ]
    conservative_collisions = _collisions(
        masters,
        aliases,
        normalize_skill_name_conservative,
        normalize_alias_candidate_conservative,
    )
    return NormalizationAuditReport(
        master_count=len(masters),
        alias_count=len(aliases),
        stored_alias_mismatch_current=sum(
            normalize_skill_name(alias) != stored for alias, stored in pairs
        ),
        stored_alias_mismatch_conservative=sum(
            normalize_skill_name_conservative(alias) != stored
            for alias, stored in pairs
        ),
        current=_strategy_audit(
            "CURRENT_REMOVE_SEPARATORS",
            cases,
            masters,
            aliases,
            normalize_skill_name,
            normalize_alias_candidate,
        ),
        conservative=_strategy_audit(
            "CONSERVATIVE_WHITESPACE",
            cases,
            masters,
            aliases,
            normalize_skill_name_conservative,
            normalize_alias_candidate_conservative,
        ),
        conservative_collision_samples=dict(
            list(sorted(conservative_collisions.items()))[:20]
        ),
    )

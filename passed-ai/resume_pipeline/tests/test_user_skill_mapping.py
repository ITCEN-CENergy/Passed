from __future__ import annotations

import pytest

from resume_pipeline.skill_extraction_models import (
    ExtractedChunkSkills,
    FailedChunkExtraction,
    SkillCandidate,
    SkillCategory,
    SkillExtractionReport,
)
from resume_pipeline.skill_mapping_models import MappingMethod
from resume_pipeline.skill_mapping_worker import SkillAlias, SkillMaster
from resume_pipeline.skill_recall_worker import (
    ChunkPass2Result,
    Pass2PreviewReport,
    VerifiedMissingSkill,
)
from resume_pipeline.user_skill_mapping_models import (
    AggregatedUserSkill,
    MappedEvidence,
    ProcessedChunkRef,
    UserSkillMappingReport,
)
from resume_pipeline import user_skill_mapping_worker as worker


def _evidence(
    *,
    skill_id=1,
    name="협업",
    category=SkillCategory.BEHAVIORAL_TRAIT,
    source="RESUME",
    chunk_id=1,
    text="역할을 나누어 작업했습니다.",
    level=2,
    method=MappingMethod.ALIAS,
):
    return MappedEvidence(
        skill_id=skill_id,
        skill_name=name,
        category=category,
        source_kind=source,
        chunk_id=chunk_id,
        context_type="EXPERIENCE",
        content_hash=f"hash-{source}-{chunk_id}",
        extracted_name=text,
        evidence=text,
        extracted_level=level,
        mapping_method=method,
        mapping_confidence=1.0 if method is MappingMethod.EXACT else 0.95,
    )


def test_behavioral_level_is_ownership_value_one_after_overlap_dedup():
    mapped = [
        _evidence(chunk_id=1),
        _evidence(source="COVER_LETTER", chunk_id=2),  # 같은 문장 overlap
        _evidence(chunk_id=3, text="팀원과 API 규격을 합의했습니다."),
    ]

    skill = worker.aggregate_mapped_evidences(mapped)[0]

    assert skill.level == 1
    assert len(skill.evidences) == 2
    assert skill.level_confidence == 0.9


def test_technical_level_uses_max_instead_of_evidence_count():
    mapped = [
        _evidence(
            skill_id=2,
            name="Java",
            category=SkillCategory.TECHNICAL_SKILL,
            chunk_id=1,
            text="Java를 학습했습니다.",
            level=1,
            method=MappingMethod.EXACT,
        ),
        _evidence(
            skill_id=2,
            name="Java",
            category=SkillCategory.TECHNICAL_SKILL,
            chunk_id=2,
            text="Java로 결제 API를 운영했습니다.",
            level=3,
            method=MappingMethod.EXACT,
        ),
    ]

    skill = worker.aggregate_mapped_evidences(mapped)[0]

    assert skill.level == 3
    assert skill.mapping_confidence == 1.0


def test_strict_pass2_recovery_merges_by_master_and_fixes_behavioral_level():
    extraction = SkillExtractionReport(
        user_id=19,
        model="fake",
        chunks=[
            ExtractedChunkSkills(
                source_kind="RESUME",
                chunk_id=1,
                context_type="EXPERIENCE",
                content_hash="hash-RESUME-1",
                skills=[],
            )
        ],
    )
    pass1 = _report()
    pass2 = Pass2PreviewReport(
        model="fake",
        verifier_mode="strict",
        chunks=[
            ChunkPass2Result(
                source_kind="RESUME",
                chunk_id=1,
                content_hash="hash-RESUME-1",
                proposed_count=1,
                verified=[
                    VerifiedMissingSkill(
                        skill_id=3,
                        name="협업",
                        category=SkillCategory.BEHAVIORAL_TRAIT,
                        evidence="역할을 나누어 작업했습니다.",
                        level=3,
                        retrieval_similarity=0.82,
                    )
                ],
            )
        ],
    )

    merged = worker.merge_verified_pass2_skills(extraction, pass1, pass2)

    assert [skill.skill_name for skill in merged.skills] == ["협업", "Java"]
    behavioral = next(
        skill for skill in merged.skills if skill.category is SkillCategory.BEHAVIORAL_TRAIT
    )
    assert behavioral.level == 1
    assert behavioral.evidences[0].mapping_method is MappingMethod.EMBEDDING


def test_actual_extraction_maps_exact_and_alias_without_embedding(monkeypatch):
    extraction = SkillExtractionReport(
        user_id=19,
        model="fake",
        chunks=[
            ExtractedChunkSkills(
                source_kind="RESUME",
                chunk_id=5,
                context_type="EXPERIENCE",
                content_hash="hash",
                skills=[
                    SkillCandidate(
                        extracted_name="Java",
                        category=SkillCategory.TECHNICAL_SKILL,
                        level=2,
                        evidence="Java로 개발했습니다.",
                    ),
                    SkillCandidate(
                        extracted_name="역할 분담",
                        category=SkillCategory.BEHAVIORAL_TRAIT,
                        level=2,
                        evidence="역할 분담을 했습니다.",
                    ),
                ],
            )
        ],
    )
    masters = [
        SkillMaster(1, "Java", SkillCategory.TECHNICAL_SKILL, True),
        SkillMaster(2, "협업", SkillCategory.BEHAVIORAL_TRAIT, True),
    ]
    aliases = [
        SkillAlias(1, 2, "협업", SkillCategory.BEHAVIORAL_TRAIT, "역할 분담")
    ]
    monkeypatch.setattr(worker, "load_skill_masters", lambda conn: masters)
    monkeypatch.setattr(worker, "load_skill_aliases", lambda conn: aliases)

    mapped, unmapped = worker.map_extracted_candidates(object(), extraction)

    assert [item.skill_name for item in mapped] == ["Java", "협업"]
    assert [item.mapping_method for item in mapped] == [
        MappingMethod.EXACT,
        MappingMethod.ALIAS,
    ]
    assert not unmapped


class FakeCursor:
    def __init__(self):
        self.statements: list[str] = []
        self.rowcount = 0
        self._next_id = 100
        self._rows = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def execute(self, sql, params=()):
        normalized = " ".join(sql.split())
        self.statements.append(normalized)
        if normalized.startswith("SELECT rc.id"):
            self._rows = [{"id": 1, "content_hash": "hash-RESUME-1"}]
            self.rowcount = 1
        elif normalized.startswith("SELECT cc.id"):
            self._rows = [{"id": 2, "content_hash": "hash-COVER_LETTER-2"}]
            self.rowcount = 1
        elif normalized.startswith("DELETE FROM user_skill_evidences"):
            self.rowcount = 3
        elif normalized.startswith("DELETE FROM user_skills"):
            self.rowcount = 1
        else:
            self.rowcount = 1

    def fetchone(self):
        self._next_id += 1
        return {"id": self._next_id}

    def fetchall(self):
        return list(self._rows)


class FakeConnection:
    def __init__(self):
        self.cursor_instance = FakeCursor()

    def cursor(self):
        return self.cursor_instance


def _report(*, failures=None):
    evidence = _evidence(
        skill_id=2,
        name="Java",
        category=SkillCategory.TECHNICAL_SKILL,
        method=MappingMethod.EXACT,
    )
    skill = AggregatedUserSkill(
        skill_id=2,
        skill_name="Java",
        category=SkillCategory.TECHNICAL_SKILL,
        level=2,
        mapping_confidence=1.0,
        level_confidence=0.73,
        evidences=[evidence],
    )
    return UserSkillMappingReport(
        user_id=19,
        extraction_model="fake",
        processed_chunk_count=1,
        processed_chunks=[
            ProcessedChunkRef(
                source_kind="RESUME",
                chunk_id=1,
                content_hash="hash-RESUME-1",
            )
        ],
        skills=[skill],
        extraction_failures=failures or [],
    )


def test_persist_replaces_evidence_preserves_important_and_deletes_orphans():
    conn = FakeConnection()

    stats = worker.persist_user_skill_mapping(conn, _report())

    sql = "\n".join(conn.cursor_instance.statements)
    upsert = next(
        statement
        for statement in conn.cursor_instance.statements
        if statement.startswith("INSERT INTO user_skills")
    )
    assert "is_important" not in upsert
    assert "DELETE FROM user_skill_evidences" in sql
    assert "NOT EXISTS" in sql
    assert stats.evidence_deleted == 3
    assert stats.evidence_inserted == 1
    assert stats.skill_deleted == 1


def test_persist_refuses_partial_extraction_before_deleting_old_evidence():
    conn = FakeConnection()
    failure = FailedChunkExtraction(
        source_kind="RESUME", chunk_id=5, error="timeout"
    )

    with pytest.raises(ValueError, match="추출 실패"):
        worker.persist_user_skill_mapping(conn, _report(failures=[failure]))

    assert not conn.cursor_instance.statements


def test_successful_empty_result_deletes_skills_with_no_remaining_evidence():
    conn = FakeConnection()
    report = UserSkillMappingReport(
        user_id=19,
        extraction_model="fake",
        processed_chunk_count=2,
        processed_chunks=[
            ProcessedChunkRef(
                source_kind="RESUME",
                chunk_id=1,
                content_hash="hash-RESUME-1",
            ),
            ProcessedChunkRef(
                source_kind="COVER_LETTER",
                chunk_id=2,
                content_hash="hash-COVER_LETTER-2",
            ),
        ],
        skills=[],
    )

    stats = worker.persist_user_skill_mapping(conn, report)

    sql = "\n".join(conn.cursor_instance.statements)
    assert "DELETE FROM user_skill_evidences" in sql
    assert "DELETE FROM user_skills" in sql
    assert stats.skill_upserted == 0
    assert stats.evidence_inserted == 0


def test_zero_processed_chunks_never_wipes_existing_skills():
    conn = FakeConnection()
    report = UserSkillMappingReport(
        user_id=19,
        extraction_model="fake",
        processed_chunk_count=0,
        skills=[],
    )

    with pytest.raises(ValueError, match="처리된 청크가 0건"):
        worker.persist_user_skill_mapping(conn, report)

    assert not conn.cursor_instance.statements


def test_stale_content_hash_stops_before_evidence_delete():
    conn = FakeConnection()
    report = _report()
    report.processed_chunks[0].content_hash = "stale-hash"

    with pytest.raises(ValueError, match="청크가 변경"):
        worker.persist_user_skill_mapping(conn, report)

    assert not any(
        statement.startswith("DELETE")
        for statement in conn.cursor_instance.statements
    )

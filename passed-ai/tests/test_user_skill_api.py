from contextlib import contextmanager
from types import SimpleNamespace

from fastapi.testclient import TestClient
import pytest

from api.features.user_skill.schema import UserSkillExtractionResponse
from api.features.user_skill import router as user_skill_router
from api.features.user_skill import service
from app.main import app
from resume_pipeline.user_skill_analysis_state import (
    AnalysisFingerprint,
    ReusableAnalysisState,
)


client = TestClient(app)


def test_user_skill_extraction_endpoint_returns_camel_case_response(monkeypatch):
    monkeypatch.setattr(
        user_skill_router,
        "run_user_skill_analysis",
        lambda user_id: UserSkillExtractionResponse(
            user_id=user_id,
            processed_chunk_count=7,
            skill_count=5,
            unmapped_count=1,
            persisted=True,
            resume_chunks_embedded=3,
            cover_letter_chunks_embedded=2,
        ),
    )

    response = client.post(
        "/api/v1/user-skills/extractions",
        json={"userId": 257},
    )

    assert response.status_code == 200
    assert response.json() == {
        "userId": 257,
        "processedChunkCount": 7,
        "skillCount": 5,
        "unmappedCount": 1,
        "persisted": True,
        "resumeChunksEmbedded": 3,
        "coverLetterChunksEmbedded": 2,
    }


def test_user_skill_extraction_endpoint_rejects_invalid_user_id():
    response = client.post(
        "/api/v1/user-skills/extractions",
        json={"userId": 0},
    )

    assert response.status_code == 422


def test_pipeline_runs_chunking_embedding_mapping_and_persistence(monkeypatch):
    calls: list[str] = []
    fake_connection = object()

    @contextmanager
    def connection():
        yield fake_connection

    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.setattr(service, "connection", connection)
    monkeypatch.setattr(
        service,
        "validate_embedding_schema",
        lambda conn: calls.append("validate-schema"),
    )
    monkeypatch.setattr(
        service,
        "run_chunking_for_user",
        lambda conn, user_id: calls.append("chunk"),
    )
    fingerprint = AnalysisFingerprint(
        document_hash="a" * 64,
        pipeline_hash="b" * 64,
        processed_chunk_count=6,
    )
    monkeypatch.setattr(
        service,
        "build_analysis_fingerprint",
        lambda conn, user_id: calls.append("fingerprint") or fingerprint,
    )
    monkeypatch.setattr(
        service,
        "load_reusable_analysis_state",
        lambda conn, user_id, value: calls.append("cache-miss") or None,
    )
    monkeypatch.setattr(
        service,
        "_retry_failed_embeddings",
        lambda conn, user_id: calls.append("retry-failed"),
    )

    def embed(conn, table, **kwargs):
        calls.append(f"embed:{table}")
        return SimpleNamespace(embedded=2 if table == "resume_chunks" else 1, failed=0)

    extraction = SimpleNamespace(failures=[])
    report = SimpleNamespace(
        processed_chunk_count=6,
        skills=[object(), object(), object(), object()],
        unmapped=[SimpleNamespace(
            source_kind="resume",
            chunk_id=31,
            extracted_name="미매핑 기술",
            category=SimpleNamespace(value="TECHNICAL_SKILL"),
            failure_reason=SimpleNamespace(value="NO_MATCH"),
            evidence="근거 문장",
        )],
    )
    monkeypatch.setattr(service, "embed_pending_chunks", embed)
    monkeypatch.setattr(
        service,
        "extract_user_skill_candidates",
        lambda conn, user_id: calls.append("extract") or extraction,
    )
    monkeypatch.setattr(
        service,
        "build_user_skill_mapping_report",
        lambda conn, value: calls.append("map") or report,
    )
    monkeypatch.setattr(
        service,
        "build_pass1_strict_validation_retrieval",
        lambda conn, extraction, mapping: calls.append("build-pass1-validation")
        or object(),
    )

    def verify(retrieval, **kwargs):
        stage = "verify-pass2" if "pass1_mapping" in kwargs else "verify-pass1"
        calls.append(stage)
        return SimpleNamespace(verified_count=0)

    monkeypatch.setattr(service, "verify_retrieval_with_pass2", verify)
    monkeypatch.setattr(
        service,
        "filter_pass1_mapping_with_strict_validation",
        lambda mapping, validation: calls.append("filter-pass1") or report,
    )
    monkeypatch.setattr(
        service,
        "retrieve_missing_master_candidates",
        lambda conn, extraction, mapping, **kwargs: calls.append("retrieve-pass2")
        or object(),
    )
    monkeypatch.setattr(
        service,
        "merge_verified_pass2_skills",
        lambda extraction, mapping, verified: calls.append("merge-pass2") or report,
    )
    monkeypatch.setattr(
        service,
        "persist_user_skill_mapping",
        lambda conn, value: calls.append("persist"),
    )
    monkeypatch.setattr(
        service,
        "save_analysis_state",
        lambda conn, user_id, value, **kwargs: calls.append("save-state"),
    )

    response = service.run_user_skill_analysis(257)

    assert calls == [
        "validate-schema",
        "chunk",
        "fingerprint",
        "cache-miss",
        "retry-failed",
        "embed:resume_chunks",
        "embed:cover_letter_chunks",
        "extract",
        "map",
        "build-pass1-validation",
        "verify-pass1",
        "filter-pass1",
        "retrieve-pass2",
        "verify-pass2",
        "merge-pass2",
        "persist",
        "save-state",
    ]
    assert response.user_id == 257
    assert response.skill_count == 4
    assert response.unmapped_count == 1
    assert response.persisted is True


def test_pipeline_does_not_start_without_openai_key(monkeypatch):
    @contextmanager
    def connection():
        yield object()

    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.setattr(service, "connection", connection)
    monkeypatch.setattr(service, "validate_embedding_schema", lambda conn: None)
    monkeypatch.setattr(service, "run_chunking_for_user", lambda conn, user_id: None)
    monkeypatch.setattr(
        service,
        "build_analysis_fingerprint",
        lambda conn, user_id: AnalysisFingerprint(
            document_hash="a" * 64,
            pipeline_hash="b" * 64,
            processed_chunk_count=1,
        ),
    )
    monkeypatch.setattr(
        service,
        "load_reusable_analysis_state",
        lambda conn, user_id, fingerprint: None,
    )

    with pytest.raises(service.UserSkillPipelineConfigurationError):
        service.run_user_skill_analysis(257)


def test_unchanged_analysis_reuses_state_without_openai_calls(monkeypatch):
    calls: list[str] = []

    @contextmanager
    def connection():
        yield object()

    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.setattr(service, "connection", connection)
    monkeypatch.setattr(
        service,
        "validate_embedding_schema",
        lambda conn: calls.append("validate-schema"),
    )
    monkeypatch.setattr(
        service,
        "run_chunking_for_user",
        lambda conn, user_id: calls.append("chunk"),
    )
    fingerprint = AnalysisFingerprint(
        document_hash="a" * 64,
        pipeline_hash="b" * 64,
        processed_chunk_count=24,
    )
    monkeypatch.setattr(
        service,
        "build_analysis_fingerprint",
        lambda conn, user_id: calls.append("fingerprint") or fingerprint,
    )
    monkeypatch.setattr(
        service,
        "load_reusable_analysis_state",
        lambda conn, user_id, value: calls.append("cache-hit")
        or ReusableAnalysisState(
            processed_chunk_count=24,
            skill_count=47,
            unmapped_count=66,
        ),
    )
    monkeypatch.setattr(
        service,
        "embed_pending_chunks",
        lambda *args, **kwargs: pytest.fail("embedding must not run"),
    )
    monkeypatch.setattr(
        service,
        "extract_user_skill_candidates",
        lambda *args, **kwargs: pytest.fail("LLM extraction must not run"),
    )

    response = service.run_user_skill_analysis(257)

    assert calls == ["validate-schema", "chunk", "fingerprint", "cache-hit"]
    assert response.skill_count == 47
    assert response.unmapped_count == 66
    assert response.persisted is True
    assert response.resume_chunks_embedded == 0
    assert response.cover_letter_chunks_embedded == 0

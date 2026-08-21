from types import SimpleNamespace

from resume_pipeline.skill_extraction_models import SkillCategory
from resume_pipeline.skill_recall_worker import (
    BehavioralDirectnessDecision,
    BehavioralDirectnessResponse,
    ChunkRetrieval,
    MasterRetrievalHit,
    Pass2Response,
    Pass2Selection,
    RetrievalExpectation,
    RetrievalReport,
    StrictPass2Response,
    StrictPass2Selection,
    _merge_retrieval_hits,
    _passes_retrieval_similarity_floor,
    _select_category_balanced_hits,
    _contains_direct_mention,
    _is_behavioral_evidence_only_generic,
    _is_strict_intent_evidence,
    _retrieval_metric,
    _split_retrieval_sentences,
    verify_retrieval_with_pass2,
)


def _hit(skill_id: int, name: str, category: SkillCategory):
    return MasterRetrievalHit(
        skill_id=skill_id,
        name=name,
        category=category,
        description=f"{name} 설명",
        similarity=0.82,
    )


def _chunk() -> ChunkRetrieval:
    return ChunkRetrieval(
        source_kind="RESUME",
        chunk_id=10,
        context_type="EXPERIENCE",
        content_hash="a" * 64,
        chunk_content="Vector DB를 활용하여 검색 기능을 구현했습니다.",
        candidates=[
            _hit(1, "벡터 데이터베이스", SkillCategory.TECHNICAL_SKILL),
            _hit(2, "협업", SkillCategory.BEHAVIORAL_TRAIT),
        ],
    )


def test_retrieval_metric_measures_expected_master_recall_by_category():
    chunks = [_chunk()]
    expectations = [
        RetrievalExpectation(
            content_hash="a" * 64,
            skill_name="벡터 데이터베이스",
            category=SkillCategory.TECHNICAL_SKILL,
        ),
        RetrievalExpectation(
            content_hash="a" * 64,
            skill_name="책임감",
            category=SkillCategory.BEHAVIORAL_TRAIT,
        ),
    ]

    metric = _retrieval_metric(chunks, expectations)

    assert metric.expected_count == 2
    assert metric.found_count == 1
    assert metric.recall_at_k == 0.5
    assert metric.by_category["TECHNICAL_SKILL"] == 1.0
    assert metric.by_category["BEHAVIORAL_TRAIT"] == 0.0


class _FakeResponses:
    def parse(self, **_kwargs):
        return SimpleNamespace(
            output_parsed=Pass2Response(
                verified=[
                    Pass2Selection(
                        skill_id=1,
                        evidence="Vector DB를 활용하여 검색 기능을 구현했습니다.",
                        level=2,
                    ),
                    Pass2Selection(
                        skill_id=999,
                        evidence="Vector DB를 활용하여 검색 기능을 구현했습니다.",
                        level=2,
                    ),
                ]
            )
        )


def test_pass2_accepts_only_proposed_ids_with_grounded_evidence():
    retrieval = RetrievalReport(
        user_id=1,
        top_k_per_category=20,
        categories=[
            SkillCategory.TECHNICAL_SKILL,
            SkillCategory.BEHAVIORAL_TRAIT,
        ],
        chunks=[_chunk()],
    )
    client = SimpleNamespace(responses=_FakeResponses())

    report = verify_retrieval_with_pass2(retrieval, client=client)

    assert report.verified_count == 1
    assert report.chunks[0].verified[0].name == "벡터 데이터베이스"
    assert report.chunks[0].rejected_invalid_ids == [999]


def test_sentence_retrieval_split_does_not_change_persisted_chunk():
    content = (
        "LangChain으로 RAG를 구현했습니다.\n"
        "Docker로 서비스를 배포했습니다. 장애 로그를 분석했습니다."
    )

    sentences = _split_retrieval_sentences(content)

    assert sentences == [
        "LangChain으로 RAG를 구현했습니다.",
        "Docker로 서비스를 배포했습니다.",
        "장애 로그를 분석했습니다.",
    ]
    assert content.endswith("장애 로그를 분석했습니다.")


def test_sentence_hits_keep_best_sentence_and_final_top_k():
    weak = _hit(1, "Docker", SkillCategory.TECHNICAL_SKILL).model_copy(
        update={
            "similarity": 0.61,
            "matched_sentence": "서비스를 운영했습니다.",
            "sentence_index": 0,
        }
    )
    strong = weak.model_copy(
        update={
            "similarity": 0.88,
            "matched_sentence": "Docker로 서비스를 배포했습니다.",
            "sentence_index": 1,
        }
    )
    rag = _hit(2, "RAG", SkillCategory.TECHNICAL_SKILL).model_copy(
        update={"similarity": 0.75, "sentence_index": 2}
    )

    merged = _merge_retrieval_hits([weak, rag, strong], limit=1)

    assert len(merged) == 1
    assert merged[0].skill_id == 1
    assert merged[0].similarity == 0.88
    assert merged[0].matched_sentence == "Docker로 서비스를 배포했습니다."


def test_hybrid_hits_use_stronger_source_with_same_final_budget():
    chunk_docker = _hit(
        1, "Docker", SkillCategory.TECHNICAL_SKILL
    ).model_copy(update={"similarity": 0.62, "retrieval_source": "chunk"})
    sentence_docker = chunk_docker.model_copy(
        update={
            "similarity": 0.84,
            "retrieval_source": "sentence",
            "matched_sentence": "Docker로 서비스를 배포했습니다.",
            "sentence_index": 1,
        }
    )
    chunk_rag = _hit(2, "RAG", SkillCategory.TECHNICAL_SKILL).model_copy(
        update={"similarity": 0.79, "retrieval_source": "chunk"}
    )

    merged = _merge_retrieval_hits(
        [chunk_docker, sentence_docker, chunk_rag],
        limit=2,
    )

    assert [hit.skill_id for hit in merged] == [1, 2]
    assert merged[0].retrieval_source == "sentence"
    assert merged[0].matched_sentence == "Docker로 서비스를 배포했습니다."


def test_final_top_k_preserves_each_retrieval_category():
    technical = [
        _hit(index, f"tech-{index}", SkillCategory.TECHNICAL_SKILL).model_copy(
            update={"similarity": 0.95 - index / 100}
        )
        for index in range(1, 8)
    ]
    experience = [
        _hit(100 + index, f"exp-{index}", SkillCategory.EXPERIENCE).model_copy(
            update={"similarity": 0.75 - index / 100}
        )
        for index in range(1, 4)
    ]
    behavioral = [
        _hit(
            200 + index,
            f"behavior-{index}",
            SkillCategory.BEHAVIORAL_TRAIT,
        ).model_copy(update={"similarity": 0.45 - index / 100})
        for index in range(1, 3)
    ]

    selected = _select_category_balanced_hits(
        [*technical, *experience, *behavioral],
        limit=6,
    )

    assert len(selected) == 6
    assert {hit.category for hit in selected} == {
        SkillCategory.TECHNICAL_SKILL,
        SkillCategory.EXPERIENCE,
        SkillCategory.BEHAVIORAL_TRAIT,
    }


def test_behavioral_retrieval_floor_rejects_remote_trait_only():
    remote_behavior = _hit(
        1,
        "behavior",
        SkillCategory.BEHAVIORAL_TRAIT,
    ).model_copy(update={"similarity": 0.249})
    direct_behavior = remote_behavior.model_copy(update={"similarity": 0.41})
    low_technical = _hit(
        2,
        "technical",
        SkillCategory.TECHNICAL_SKILL,
    ).model_copy(update={"similarity": 0.20})

    assert not _passes_retrieval_similarity_floor(remote_behavior)
    assert _passes_retrieval_similarity_floor(direct_behavior)
    assert _passes_retrieval_similarity_floor(low_technical)


def test_strict_direct_mention_accepts_canonical_or_alias_only():
    docker = _hit(1, "Docker", SkillCategory.TECHNICAL_SKILL).model_copy(
        update={"aliases": ["도커"]}
    )

    assert _contains_direct_mention("Docker로 서비스를 배포했습니다.", docker)
    assert _contains_direct_mention("도커 컨테이너를 구성했습니다.", docker)
    assert not _contains_direct_mention("AWS에 서비스를 배포했습니다.", docker)


def test_strict_direct_mention_does_not_accept_parent_concept_for_product():
    ec2 = _hit(1655, "AWS EC2", SkillCategory.TECHNICAL_SKILL).model_copy(
        update={"aliases": ["EC2"]}
    )

    assert not _contains_direct_mention("AWS에 서비스를 배포했습니다.", ec2)
    assert _contains_direct_mention("AWS EC2에 서비스를 배포했습니다.", ec2)


def test_strict_intent_rejects_future_desire_without_skill_name_rules():
    assert _is_strict_intent_evidence(
        "생성형 AI 기능을 운영하는 개발자가 되고 싶어 지원했습니다."
    )
    assert not _is_strict_intent_evidence(
        "생성형 AI 기반 콘텐츠 생성 플랫폼을 개발했습니다."
    )


def test_behavioral_generic_tokens_are_only_an_early_empty_content_filter():
    assert _is_behavioral_evidence_only_generic(
        "업무 역량을 개선하고 기준을 적용했습니다."
    )
    assert not _is_behavioral_evidence_only_generic(
        "공식 문서를 학습한 뒤 FastAPI 서버를 구현했습니다."
    )


class _FakeStrictResponses:
    def parse(self, **kwargs):
        if kwargs["text_format"] is BehavioralDirectnessResponse:
            return SimpleNamespace(
                output_parsed=BehavioralDirectnessResponse(
                    decisions=[
                        BehavioralDirectnessDecision(
                            skill_id=1344,
                            accept=True,
                            requires_trait_inference=False,
                            directly_matches_definition=True,
                            mere_related_action=False,
                            missing_definition_elements=[],
                            reason="DIRECT_OBSERVABLE_ACTION",
                        )
                    ]
                )
            )
        assert kwargs["text_format"] is StrictPass2Response
        return SimpleNamespace(
            output_parsed=StrictPass2Response(
                verified=[
                    StrictPass2Selection(
                        skill_id=1344,
                        evidence=(
                            "처음 사용하는 FastAPI 공식 문서를 학습한 뒤 API "
                            "서버를 구현하여 프로젝트에 적용했습니다."
                        ),
                        observable_action=(
                            "처음 사용하는 FastAPI 공식 문서를 학습한 뒤 API "
                            "서버를 구현하여 프로젝트에 적용했습니다."
                        ),
                        verification_basis="OBSERVABLE_COMPLETED_ACTION",
                        level=2,
                    )
                ]
            )
        )


def test_strict_behavioral_uses_grounded_observable_completed_action_contract():
    content = (
        "처음 사용하는 FastAPI 공식 문서를 학습한 뒤 API 서버를 구현하여 "
        "프로젝트에 적용했습니다."
    )
    learning = _hit(
        1344, "학습 민첩성", SkillCategory.BEHAVIORAL_TRAIT
    ).model_copy(
        update={
            "description": "새로운 지식과 도구를 학습하고 실제 과업에 적용하는 역량입니다."
        }
    )
    retrieval = RetrievalReport(
        user_id=1,
        top_k_per_category=40,
        categories=[SkillCategory.BEHAVIORAL_TRAIT],
        chunks=[
            ChunkRetrieval(
                source_kind="RESUME",
                chunk_id=15,
                context_type="EXPERIENCE",
                content_hash="b" * 64,
                chunk_content=content,
                candidates=[learning],
            )
        ],
    )

    report = verify_retrieval_with_pass2(
        retrieval,
        client=SimpleNamespace(responses=_FakeStrictResponses()),
        strict=True,
    )

    assert report.verified_count == 1
    assert report.chunks[0].verified[0].name == "학습 민첩성"

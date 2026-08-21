import json

from api.features.coverletter import service
from api.features.coverletter.schema import CoverLetterEditRequest


class RecordingChain:
    def __init__(self, response):
        self.response = response
        self.calls = []

    def invoke(self, values):
        self.calls.append(values)
        return self.response


class StructuredOutputLlm:
    def __init__(self):
        self.calls = []

    def __call__(self, value):
        return value

    def with_structured_output(self, schema, **kwargs):
        self.calls.append((schema, kwargs))
        return self


def item_feedback(score=82):
    return {
        "score": score,
        "shortcomings": "근거가 부족합니다.",
        "recommended_revision_direction": "구체적인 사례를 보완하세요.",
    }


def test_edit_request_defaults_character_limit_to_1200_and_allows_no_limit():
    request = CoverLetterEditRequest(question="질문", content="답변")
    unlimited_request = CoverLetterEditRequest(
        question="질문", content="답변", character_limit=None
    )

    assert request.character_limit == 1200
    assert unlimited_request.character_limit is None


def test_feedback_analysis_returns_requested_structure_without_suggestion(monkeypatch):
    feedback_chain = RecordingChain(item_feedback())
    final_chain = RecordingChain("최종 답변")
    monkeypatch.setattr(service, "_create_cover_letter_chains", lambda: (feedback_chain, final_chain))

    result = service.process_cover_letter_chain("지원 동기는?", "원본 답변", "채용 공고")

    assert feedback_chain.calls == [{
        "question": "지원 동기는?",
        "content": "원본 답변",
        "job_description": "채용 공고",
        "user_skills_json": "[]",
    }]
    assert final_chain.calls == []
    assert result == {
        "qa_alignment_score": 82,
        "shortcomings": "근거가 부족합니다.",
        "recommended_revision_direction": "구체적인 사례를 보완하세요.",
    }


def test_cover_letter_chains_enforce_openai_json_schema(monkeypatch):
    llm = StructuredOutputLlm()
    monkeypatch.setattr(service, "ChatOpenAI", lambda **kwargs: llm)

    service._create_cover_letter_chains()
    service._create_overall_review_chain()

    assert llm.calls == [
        (service.ItemFeedbackOutput, {"method": "json_schema"}),
        (service.OverallReviewOutput, {"method": "json_schema"}),
    ]


def test_nested_feedback_fields_are_normalized_to_strings(monkeypatch):
    feedback_chain = RecordingChain({
        "score": 76,
        "shortcomings": {
            "content_specificity": "성과가 구체적이지 않습니다.",
            "logical_flow": "경험과 지원 동기의 연결이 약합니다.",
        },
        "recommended_revision_direction": {
            "improvement_suggestion": "본인의 역할을 먼저 제시해 주세요.",
        },
    })
    monkeypatch.setattr(
        service,
        "_create_cover_letter_chains",
        lambda: (feedback_chain, RecordingChain("첨삭 답변")),
    )

    result = service.process_cover_letter_chain("질문", "답변", "채용 공고")

    assert result["shortcomings"] == (
        "내용 구체성: 성과가 구체적이지 않습니다.\n"
        "논리성: 경험과 지원 동기의 연결이 약합니다."
    )
    assert result["recommended_revision_direction"] == (
        "개선 방향: 본인의 역할을 먼저 제시해 주세요."
    )


def test_suggested_answer_uses_both_feedback_sections(monkeypatch):
    feedback_chain = RecordingChain(item_feedback())
    final_chain = RecordingChain("추천 수정안")
    monkeypatch.setattr(service, "_create_cover_letter_chains", lambda: (feedback_chain, final_chain))

    result = service.process_cover_letter_suggestion_chain("질문", "답변", "공고", character_limit=700)

    assert result == "추천 수정안"
    assert final_chain.calls[0]["shortcomings"] == "근거가 부족합니다."
    assert final_chain.calls[0]["recommended_revision_direction"] == "구체적인 사례를 보완하세요."
    assert final_chain.calls[0]["character_limit"] == 700


def test_suggested_answer_is_truncated_to_character_limit(monkeypatch):
    feedback_chain = RecordingChain(item_feedback())
    final_chain = RecordingChain("가나다라마바사")
    monkeypatch.setattr(service, "_create_cover_letter_chains", lambda: (feedback_chain, final_chain))

    result = service.process_cover_letter_suggestion_chain(
        "질문", "답변", "공고", character_limit=5
    )

    assert result == "가나다라마"


def test_user_skills_are_sent_to_feedback_and_suggestion_prompts(monkeypatch):
    feedback_chain = RecordingChain(item_feedback())
    final_chain = RecordingChain("추천 수정안")
    monkeypatch.setattr(service, "_create_cover_letter_chains", lambda: (feedback_chain, final_chain))
    skills = [{
        "skill_id": 9,
        "name": "Spring Boot",
        "category": "TECHNICAL_SKILL",
        "level": 2,
    }]

    service.process_cover_letter_suggestion_chain("질문", "답변", "공고", skills)

    expected = json.dumps(skills, ensure_ascii=False)
    assert feedback_chain.calls[0]["user_skills_json"] == expected
    assert final_chain.calls[0]["user_skills_json"] == expected


def test_suggestion_prompt_renders_user_skills():
    skills_json = json.dumps([{
        "skill_id": 9,
        "name": "Spring Boot",
        "category": "TECHNICAL_SKILL",
        "level": 2,
    }], ensure_ascii=False)

    messages = service.final_edit_prompt.format_messages(
        question="지원 동기는?",
        content="Spring Boot로 API를 개발했습니다.",
        shortcomings="직무 연관성이 부족합니다.",
        recommended_revision_direction="보유 스킬과 경험을 연결하세요.",
        user_skills_json=skills_json,
        character_limit=1200,
    )

    assert "Spring Boot" in messages[-1].content
    assert "원문의 경험과 직접 관련된 사용자 보유 스킬" in messages[0].content
    assert "원문에 사용 근거가 없는 스킬" in messages[0].content
    assert "1200" in messages[-1].content


def test_review_returns_ordered_item_feedback_with_requested_structure(monkeypatch):
    monkeypatch.setattr(
        service,
        "_create_cover_letter_chains",
        lambda: (RecordingChain(item_feedback(81)), RecordingChain("첨삭 답변")),
    )
    overall_chain = RecordingChain({
        "overall_score": 79,
        "summary": "전체 요약",
        "strengths": "공통 강점",
        "improvements": "공통 개선점",
    })
    monkeypatch.setattr(service, "_create_overall_review_chain", lambda: overall_chain)

    skills = [{"skill_id": 9, "name": "Spring Boot", "category": "TECHNICAL_SKILL", "level": 2}]
    result = service.process_cover_letter_review_chain([
        {"item_id": 2, "display_order": 2, "question": "두 번째", "content": "답변 2", "character_limit": 800},
        {"item_id": 1, "display_order": 1, "question": "첫 번째", "content": "답변 1", "character_limit": 500},
    ], "비공개 분석용 채용 공고", skills)

    assert [item["item_id"] for item in result["items"]] == [1, 2]
    assert result["items"][0]["shortcomings"] == "근거가 부족합니다."
    aggregate_items = json.loads(overall_chain.calls[0]["items_json"])
    assert [item["item_id"] for item in aggregate_items] == [1, 2]
    assert json.loads(overall_chain.calls[0]["user_skills_json"]) == skills


def test_review_normalizes_overall_list_fields_to_strings(monkeypatch):
    monkeypatch.setattr(
        service,
        "_create_cover_letter_chains",
        lambda: (RecordingChain(item_feedback()), RecordingChain("첨삭 답변")),
    )
    monkeypatch.setattr(service, "_create_overall_review_chain", lambda: RecordingChain({
        "overall_score": 79,
        "summary": "전체 요약",
        "strengths": ["강점 1", "강점 2"],
        "improvements": ["개선 1", "개선 2"],
    }))

    result = service.process_cover_letter_review_chain([{
        "item_id": 1,
        "display_order": 1,
        "question": "지원 동기는?",
        "content": "지원 동기 답변",
        "character_limit": 500,
    }])

    assert result["overall_feedback"]["strengths"] == "강점 1\n강점 2"
    assert result["overall_feedback"]["improvements"] == "개선 1\n개선 2"

import json

from api.features.coverletter import service


class RecordingChain:
    def __init__(self, response):
        self.response = response
        self.calls = []

    def invoke(self, values):
        self.calls.append(values)
        return self.response


def test_feedback_analysis_does_not_generate_suggested_answer(monkeypatch):
    qa_chain = RecordingChain({"score": 82, "feedback": "맞춤법과 질문 의도 피드백"})
    jd_chain = RecordingChain("공고 적합도와 표현 피드백")
    final_chain = RecordingChain("교정 사항을 반영한 최종 답변")
    monkeypatch.setattr(
        service,
        "_create_cover_letter_chains",
        lambda: (qa_chain, jd_chain, final_chain),
    )

    result = service.process_cover_letter_chain("지원 동기는?", "원본 답변", "채용 공고")

    assert qa_chain.calls == [{"question": "지원 동기는?", "content": "원본 답변"}]
    assert jd_chain.calls == [{"job_description": "채용 공고", "content": "원본 답변"}]
    assert final_chain.calls == []
    assert result == {
        "qa_alignment_score": 82,
        "qa_alignment_feedback": "맞춤법과 질문 의도 피드백",
        "jd_fit_feedback": "공고 적합도와 표현 피드백",
    }


def test_job_feedback_is_skipped_when_job_description_is_missing(monkeypatch):
    qa_chain = RecordingChain({"score": 70, "feedback": "문항 및 표현 피드백"})
    jd_chain = RecordingChain("호출되면 안 됨")
    final_chain = RecordingChain("최종 답변")
    monkeypatch.setattr(
        service,
        "_create_cover_letter_chains",
        lambda: (qa_chain, jd_chain, final_chain),
    )

    result = service.process_cover_letter_chain("질문", "답변")

    assert jd_chain.calls == []
    assert result["jd_fit_feedback"] == "제공된 채용 공고 정보가 없습니다."
    assert final_chain.calls == []


def test_nested_qa_feedback_is_normalized_to_string(monkeypatch):
    qa_chain = RecordingChain({
        "score": 76,
        "feedback": {
            "content_specificity": "성과를 수치로 구체화해 주세요.",
            "logical_flow": "경험과 지원 동기의 연결을 보완해 주세요.",
            "improvement_suggestion": "본인의 역할을 먼저 제시해 주세요.",
        },
    })
    jd_chain = RecordingChain("직무 적합도 피드백")
    final_chain = RecordingChain("첨삭 답변")
    monkeypatch.setattr(
        service,
        "_create_cover_letter_chains",
        lambda: (qa_chain, jd_chain, final_chain),
    )

    result = service.process_cover_letter_chain("질문", "답변", "채용 공고")

    expected = (
        "내용 구체성: 성과를 수치로 구체화해 주세요.\n"
        "논리성: 경험과 지원 동기의 연결을 보완해 주세요.\n"
        "개선 방향: 본인의 역할을 먼저 제시해 주세요."
    )
    assert result["qa_alignment_feedback"] == expected
    assert final_chain.calls == []


def test_suggested_answer_is_generated_only_on_explicit_request(monkeypatch):
    qa_chain = RecordingChain({"score": 82, "feedback": "문항 피드백"})
    jd_chain = RecordingChain("직무 피드백")
    final_chain = RecordingChain("추천 수정안")
    monkeypatch.setattr(
        service,
        "_create_cover_letter_chains",
        lambda: (qa_chain, jd_chain, final_chain),
    )

    result = service.process_cover_letter_suggestion_chain("질문", "답변", "공고")

    assert result == "추천 수정안"
    assert final_chain.calls == [{
        "question": "질문",
        "content": "답변",
        "qa_alignment_feedback": "문항 피드백",
        "jd_fit_feedback": "직무 피드백",
    }]


def test_review_returns_overall_and_ordered_item_feedback_without_page_metadata(monkeypatch):
    qa_chain = RecordingChain({"score": 81, "feedback": "문항 피드백"})
    jd_chain = RecordingChain("직무 적합도 피드백")
    final_chain = RecordingChain("첨삭 답변")
    overall_chain = RecordingChain({
        "overall_score": 79,
        "summary": "전체 요약",
        "strengths": "공통 강점",
        "improvements": "공통 개선점",
    })
    monkeypatch.setattr(
        service,
        "_create_cover_letter_chains",
        lambda: (qa_chain, jd_chain, final_chain),
    )
    monkeypatch.setattr(
        service,
        "_create_overall_review_chain",
        lambda: overall_chain,
    )

    result = service.process_cover_letter_review_chain([
        {
            "item_id": 2,
            "display_order": 2,
            "question": "두 번째 질문",
            "content": "두 번째 답변",
            "character_limit": 800,
        },
        {
            "item_id": 1,
            "display_order": 1,
            "question": "첫 번째 질문",
            "content": "첫 번째 답변",
            "character_limit": 500,
        },
    ], "비공개 분석용 채용 공고")

    assert [item["item_id"] for item in result["items"]] == [1, 2]
    assert result["overall_feedback"] == {
        "overall_score": 79,
        "summary": "전체 요약",
        "strengths": "공통 강점",
        "improvements": "공통 개선점",
    }
    assert set(result) == {"overall_feedback", "items"}
    assert "job_description" not in result

    aggregate_call = overall_chain.calls[0]
    assert aggregate_call["job_description"] == "비공개 분석용 채용 공고"
    aggregate_items = json.loads(aggregate_call["items_json"])
    assert [item["item_id"] for item in aggregate_items] == [1, 2]
    assert aggregate_items[0]["content"] == "첫 번째 답변"


def test_review_normalizes_overall_list_fields_to_strings(monkeypatch):
    qa_chain = RecordingChain({"score": 81, "feedback": "문항 피드백"})
    jd_chain = RecordingChain("직무 적합도 피드백")
    final_chain = RecordingChain("첨삭 답변")
    overall_chain = RecordingChain({
        "overall_score": 79,
        "summary": "전체 요약",
        "strengths": ["지원 동기가 명확합니다.", "경험의 흐름이 자연스럽습니다."],
        "improvements": ["성과를 수치로 제시해 주세요.", "직무 연결을 강화해 주세요."],
    })
    monkeypatch.setattr(
        service,
        "_create_cover_letter_chains",
        lambda: (qa_chain, jd_chain, final_chain),
    )
    monkeypatch.setattr(service, "_create_overall_review_chain", lambda: overall_chain)

    result = service.process_cover_letter_review_chain([{
        "item_id": 1,
        "display_order": 1,
        "question": "지원 동기는?",
        "content": "지원 동기 답변",
        "character_limit": 500,
    }])

    assert result["overall_feedback"]["strengths"] == (
        "지원 동기가 명확합니다.\n경험의 흐름이 자연스럽습니다."
    )
    assert result["overall_feedback"]["improvements"] == (
        "성과를 수치로 제시해 주세요.\n직무 연결을 강화해 주세요."
    )

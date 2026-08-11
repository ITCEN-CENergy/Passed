from api.features.coverletter import service


class RecordingChain:
    def __init__(self, response):
        self.response = response
        self.calls = []

    def invoke(self, values):
        self.calls.append(values)
        return self.response


def test_feedback_and_editing_use_original_content_without_spell_check(monkeypatch):
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
    assert final_chain.calls == [{
        "question": "지원 동기는?",
        "content": "원본 답변",
        "qa_alignment_feedback": "맞춤법과 질문 의도 피드백",
        "jd_fit_feedback": "공고 적합도와 표현 피드백",
    }]
    assert result == {
        "qa_alignment_score": 82,
        "qa_alignment_feedback": "맞춤법과 질문 의도 피드백",
        "jd_fit_feedback": "공고 적합도와 표현 피드백",
        "final_edited_content": "교정 사항을 반영한 최종 답변",
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
    assert final_chain.calls[0]["jd_fit_feedback"] == "제공된 채용 공고 정보가 없습니다."

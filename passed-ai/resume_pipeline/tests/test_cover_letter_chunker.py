from __future__ import annotations

from resume_pipeline.cover_letter_chunker import (
    build_cover_letter_chunks,
    split_cover_letter,
)


def test_paragraph_boundaries_are_used_first():
    chunks = split_cover_letter(
        "첫 번째 문단입니다.\n\n두 번째 문단입니다.",
        max_chars=100,
        min_chars=1,
        overlap_chars=0,
    )
    assert chunks == ["첫 번째 문단입니다.", "두 번째 문단입니다."]


def test_long_paragraph_is_split_on_sentences():
    chunks = split_cover_letter(
        "첫 번째 문장입니다. 두 번째 문장입니다. 세 번째 문장입니다.",
        max_chars=22,
        min_chars=1,
        overlap_chars=0,
    )
    assert len(chunks) >= 2
    assert all(len(chunk) <= 22 for chunk in chunks)


def test_short_chunk_is_merged_with_neighbor():
    chunks = split_cover_letter(
        "짧은 문단.\n\n이 문단은 짧은 문단과 합쳐져도 최대 길이를 넘지 않습니다.",
        max_chars=100,
        min_chars=20,
        overlap_chars=0,
    )
    assert len(chunks) == 1
    assert "짧은 문단." in chunks[0]


def test_next_chunk_contains_previous_sentence_overlap():
    chunks = split_cover_letter(
        "프로젝트에서 Spring Boot API를 개발했습니다.\n\n응답 시간을 개선했습니다.",
        max_chars=100,
        min_chars=1,
        overlap_chars=12,
    )
    assert len(chunks) == 2
    assert "API를 개발했습니다." in chunks[1]


def test_overlap_never_starts_in_the_middle_of_a_word():
    chunks = split_cover_letter(
        "장애가 발생했을 때는 로그와 재현 테스트를 먼저 작성해 원인을 좁혔고, "
        "트랜잭션 범위를 조정하여 동일 문제가 다시 발생하지 않도록 했습니다.\n\n"
        "다음 문단입니다.",
        max_chars=200,
        min_chars=1,
        overlap_chars=50,
    )

    assert len(chunks) == 2
    assert not chunks[1].startswith("성해")
    assert chunks[1].startswith("원인을")


def test_empty_answer_removes_all_chunks_and_hash_is_deterministic():
    assert build_cover_letter_chunks(1, "   ") == []
    first = build_cover_letter_chunks(1, "문장입니다.", min_chars=1)
    second = build_cover_letter_chunks(1, "문장입니다.", min_chars=1)
    assert first == second
    assert len(first[0].content_hash) == 64

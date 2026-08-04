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
    assert chunks[1].startswith(chunks[0][-12:])


def test_empty_answer_removes_all_chunks_and_hash_is_deterministic():
    assert build_cover_letter_chunks(1, "   ") == []
    first = build_cover_letter_chunks(1, "문장입니다.", min_chars=1)
    second = build_cover_letter_chunks(1, "문장입니다.", min_chars=1)
    assert first == second
    assert len(first[0].content_hash) == 64

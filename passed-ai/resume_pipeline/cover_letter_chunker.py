"""자기소개서 답변을 문단과 문장 경계로 분할한다."""

from __future__ import annotations

import re

from .models import CoverLetterChunk
from .text_utils import content_hash, normalize_text

_PARAGRAPH_BOUNDARY = re.compile(r"\n\s*\n+")
_SENTENCE = re.compile(r".+?(?:[.!?。！？](?=\s|$)|$)", re.DOTALL)


def _sentences(text: str) -> list[str]:
    sentences = [normalize_text(match.group(0)) for match in _SENTENCE.finditer(text)]
    return [sentence for sentence in sentences if sentence]


def _hard_split(text: str, max_chars: int) -> list[str]:
    return [text[index : index + max_chars].strip() for index in range(0, len(text), max_chars)]


def _split_long_paragraph(paragraph: str, max_chars: int) -> list[str]:
    if len(paragraph) <= max_chars:
        return [paragraph]

    units: list[str] = []
    for sentence in _sentences(paragraph):
        units.extend(_hard_split(sentence, max_chars) if len(sentence) > max_chars else [sentence])

    chunks: list[str] = []
    current = ""
    for unit in units:
        candidate = f"{current} {unit}".strip()
        if current and len(candidate) > max_chars:
            chunks.append(current)
            current = unit
        else:
            current = candidate
    if current:
        chunks.append(current)
    return chunks


def _merge_short_chunks(
    chunks: list[str],
    *,
    min_chars: int,
    max_chars: int,
) -> list[str]:
    merged: list[str] = []
    index = 0
    while index < len(chunks):
        current = chunks[index]
        if len(current) < min_chars and index + 1 < len(chunks):
            candidate = f"{current}\n{chunks[index + 1]}".strip()
            if len(candidate) <= max_chars:
                merged.append(candidate)
                index += 2
                continue
        if len(current) < min_chars and merged:
            candidate = f"{merged[-1]}\n{current}".strip()
            if len(candidate) <= max_chars:
                merged[-1] = candidate
                index += 1
                continue
        merged.append(current)
        index += 1
    return merged


def _overlap_prefix(previous: str, overlap_chars: int) -> str:
    if overlap_chars <= 0:
        return ""
    sentences = _sentences(previous)
    last_sentence = sentences[-1] if sentences else previous
    return last_sentence[-overlap_chars:].strip()


def split_cover_letter(
    answer: str | None,
    *,
    max_chars: int = 400,
    min_chars: int = 100,
    overlap_chars: int = 50,
) -> list[str]:
    """문단 우선 분할 후 긴 문단 재분할·짧은 청크 병합·overlap을 적용한다."""
    if max_chars <= 0:
        raise ValueError("max_chars는 1 이상이어야 합니다.")
    if not 0 <= min_chars <= max_chars:
        raise ValueError("min_chars는 0 이상 max_chars 이하여야 합니다.")
    if overlap_chars < 0:
        raise ValueError("overlap_chars는 0 이상이어야 합니다.")

    normalized = normalize_text(answer)
    if not normalized:
        return []

    paragraphs = [
        normalize_text(paragraph)
        for paragraph in _PARAGRAPH_BOUNDARY.split(normalized)
        if normalize_text(paragraph)
    ]
    base_chunks = [
        chunk
        for paragraph in paragraphs
        for chunk in _split_long_paragraph(paragraph, max_chars)
    ]
    merged = _merge_short_chunks(base_chunks, min_chars=min_chars, max_chars=max_chars)

    overlapped: list[str] = []
    for index, chunk in enumerate(merged):
        if index == 0:
            overlapped.append(chunk)
            continue
        prefix = _overlap_prefix(merged[index - 1], overlap_chars)
        overlapped.append(f"{prefix} {chunk}".strip() if prefix else chunk)
    return overlapped


def build_cover_letter_chunks(
    cover_letter_item_id: int,
    answer: str | None,
    **split_options: int,
) -> list[CoverLetterChunk]:
    return [
        CoverLetterChunk(
            cover_letter_item_id=cover_letter_item_id,
            chunk_index=index,
            chunk_content=text,
            content_hash=content_hash(text),
        )
        for index, text in enumerate(split_cover_letter(answer, **split_options))
    ]

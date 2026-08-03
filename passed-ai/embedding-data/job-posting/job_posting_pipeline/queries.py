"""추천 조회 준비(계획서 7·14 7단계).

추천 벡터 조회 시 use_for_matching=true, embedding IS NOT NULL,
chunk_content <> '' 조건을 적용한다.
"""

from __future__ import annotations

from psycopg import Connection

# 추천 검색에서 공통으로 적용할 안전 조건이다.
MATCHING_CHUNK_WHERE = (
    "use_for_matching = true AND embedding IS NOT NULL AND chunk_content <> ''"
)


def matching_chunk_ids_for_posting(conn: Connection, job_posting_id: int) -> list[int]:
    """한 공고의 추천 대상(매칭) 청크 ID 목록을 반환."""
    # 빈 청크와 미임베딩 청크는 벡터 검색 후보에서 제외한다.
    with conn.cursor() as cur:
        cur.execute(
            f"SELECT id FROM job_posting_chunks "
            f"WHERE job_posting_id = %s AND {MATCHING_CHUNK_WHERE} "
            f"ORDER BY source_type, chunk_index",
            (job_posting_id,),
        )
        return [r[0] for r in cur.fetchall()]

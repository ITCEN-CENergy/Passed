"""채용공고 청크 생성·임베딩 파이프라인.

기존 FastAPI 서버(`app/main.py`)와는 분리된 독립 패키지다.
CSV 적재 -> job_postings UPSERT -> 청크 생성 -> LLM 구조화 추출 ->
content_hash 기반 동기화 -> 별도 임베딩 작업자 실행 순서로 동작한다.
"""

__version__ = "0.1.0"

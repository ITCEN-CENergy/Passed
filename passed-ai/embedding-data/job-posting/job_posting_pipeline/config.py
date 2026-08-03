"""환경설정.

계획서 13절에 명시된 값들을 코드에 고정하지 않고 환경변수/`.env`로 분리한다.
`<결정 필요>`로 표시된 값들은 합리적인 기본값을 채우되 env로 덮을 수 있다.

주의: 이 값들은 실제 운영 환경에서 검토 후 확정해야 한다.
- COMPANY_ASSIGNMENT_SEED: 결정적 company_id 배정용 고정 시드.
- EXTRACTION_MODEL: 구조화 추출용 LLM. structured output을 지원하는 모델 사용.
- EMBEDDING_BATCH_SIZE / EMBEDDING_MAX_RETRIES: API 비용·제한에 맞춰 조정.
"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

PROJECT_ROOT = Path(__file__).resolve().parents[3]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        # 실행 위치와 무관하게 passed-ai/.env를 읽는다.
        env_file=PROJECT_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # --- DB ---
    database_url: str = Field(
        default="postgresql://postgres:postgres@localhost:5432/postgres",
        description="PostgreSQL 접속 문자열 (psycopg)",
    )

    # --- 결정적 company_id 배정 ---
    company_id_min: int = Field(default=0, alias="COMPANY_ID_MIN")
    company_id_max: int = Field(default=159, alias="COMPANY_ID_MAX")
    company_assignment_seed: str = Field(
        default="passed-job-posting-seed-v1",
        alias="COMPANY_ASSIGNMENT_SEED",
        description="company_id=NULL 일 때 SHA-256 해시에 섞는 고정 시드",
    )

    # --- 임베딩 ---
    embedding_model: str = Field(
        default="openai/text-embedding-3-small", alias="EMBEDDING_MODEL"
    )
    embedding_dimension: int = Field(default=1536, alias="EMBEDDING_DIMENSION")
    embedding_batch_size: int = Field(default=100, alias="EMBEDDING_BATCH_SIZE")
    embedding_max_retries: int = Field(default=5, alias="EMBEDDING_MAX_RETRIES")
    embedding_only_matching: bool = Field(
        default=True,
        alias="EMBEDDING_ONLY_MATCHING",
        description="true면 use_for_matching=true 청크만 임베딩",
    )

    # --- 청킹 ---
    chunk_max_tokens: int = Field(default=400, alias="CHUNK_MAX_TOKENS")
    chunk_overlap_tokens: int = Field(default=50, alias="CHUNK_OVERLAP_TOKENS")

    # --- LLM 구조화 추출 ---
    openai_api_key: str = Field(default="", alias="OPENAI_API_KEY")
    extraction_model: str = Field(default="gpt-4.1-mini", alias="EXTRACTION_MODEL")
    extraction_prompt_version: str = Field(
        default="v1", alias="EXTRACTION_PROMPT_VERSION"
    )
    extraction_max_retries: int = Field(default=2, alias="EXTRACTION_MAX_RETRIES")
    extract_with_llm: bool = Field(
        default=True,
        alias="EXTRACT_WITH_LLM",
        description="false면 LLM 추출을 건너뛰고 tech_stack/benefit 빈 청크만 생성",
    )

    # --- 입력 해시 기반 LLM 호출 생략(권장) ---
    extraction_cache_table: str = Field(
        default="job_posting_extraction_meta",
        alias="EXTRACTION_CACHE_TABLE",
        description="LLM 추출 입력 해시/프롬프트 버전을 저장할 메타데이터 테이블",
    )


@lru_cache
def get_settings() -> Settings:
    """설정을 싱글턴으로 반환한다."""
    return Settings()

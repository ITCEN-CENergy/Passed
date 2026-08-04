"""환경설정.

계획서 13절에 명시된 값들을 코드에 고정하지 않고 환경변수/`.env`로 분리한다.
`<결정 필요>`로 표시된 값들은 합리적인 기본값을 채우되 env로 덮을 수 있다.

주의: 임베딩 배치 크기와 재시도 횟수는 API 비용·제한에 맞춰 조정해야 한다.
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
        default="postgresql://edu:1234@localhost:5433/edu",
        alias="DATABASE_URL",
        description="PostgreSQL 접속 문자열 (psycopg)",
    )
    database_connect_timeout_seconds: int = Field(
        default=10,
        ge=1,
        alias="DATABASE_CONNECT_TIMEOUT_SECONDS",
        description="DB 초기 연결 제한 시간(초)",
    )

    # --- 임베딩 ---
    embedding_model: str = Field(
        default="openai/text-embedding-3-small", alias="EMBEDDING_MODEL"
    )
    embedding_dimension: int = Field(default=1536, alias="EMBEDDING_DIMENSION")
    # 공식 API의 입력 배열 최대 개수는 2,048개이며 기본값은 보수적으로 100개다.
    embedding_batch_size: int = Field(
        default=100,
        ge=1,
        le=2048,
        alias="EMBEDDING_BATCH_SIZE",
    )
    embedding_max_retries: int = Field(
        default=5,
        ge=1,
        alias="EMBEDDING_MAX_RETRIES",
    )
    embedding_request_timeout_seconds: float = Field(
        default=60.0,
        gt=0,
        alias="EMBEDDING_REQUEST_TIMEOUT_SECONDS",
        description="OpenAI 임베딩 API 요청 timeout(초)",
    )
    embedding_only_matching: bool = Field(
        default=True,
        alias="EMBEDDING_ONLY_MATCHING",
        description="true면 비매칭 source_type 세 종류를 제외하고 임베딩",
    )

    # --- 청킹 ---
    chunk_max_tokens: int = Field(default=400, alias="CHUNK_MAX_TOKENS")
    chunk_overlap_tokens: int = Field(default=50, alias="CHUNK_OVERLAP_TOKENS")

    # --- OpenAI 임베딩 인증 ---
    openai_api_key: str = Field(default="", alias="OPENAI_API_KEY")


@lru_cache
def get_settings() -> Settings:
    """설정을 싱글턴으로 반환한다."""
    return Settings()

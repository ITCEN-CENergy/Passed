from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict
from dotenv import load_dotenv

load_dotenv()

PROJECT_ROOT = Path(__file__).resolve().parents[3]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=PROJECT_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    database_url: str = Field(
        default="postgresql://edu:1234@localhost:5433/edu",
        alias="DATABASE_URL",
    )
    openai_api_key: str = Field(default="", alias="OPENAI_API_KEY")
    embedding_model: str = Field(
        default="text-embedding-3-small", alias="SKILL_EMBEDDING_MODEL"
    )
    embedding_dimension: int = Field(
        default=1536, ge=1, alias="SKILL_EMBEDDING_DIMENSION"
    )
    embedding_batch_size: int = Field(
        default=100, ge=1, le=2048, alias="SKILL_EMBEDDING_BATCH_SIZE"
    )
    embedding_max_retries: int = Field(
        default=5, ge=1, alias="SKILL_EMBEDDING_MAX_RETRIES"
    )
    embedding_timeout_seconds: float = Field(
        default=60.0, gt=0, alias="SKILL_EMBEDDING_TIMEOUT_SECONDS"
    )
    expected_skill_count: int = Field(
        default=1654, ge=1, alias="SKILL_EXPECTED_COUNT"
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()

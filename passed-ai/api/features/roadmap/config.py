from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class RoadmapSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    generator: Literal["fake", "llm"] = Field(default="llm", alias="ROADMAP_GENERATOR")
    openai_api_key: str | None = Field(default=None, alias="OPENAI_API_KEY")
    model: str = Field(default="gpt-4o", alias="ROADMAP_LLM_MODEL")
    timeout_seconds: float = Field(default=20, gt=0, alias="ROADMAP_LLM_TIMEOUT_SECONDS")
    max_retries: int = Field(default=2, ge=0, le=5, alias="ROADMAP_LLM_MAX_RETRIES")


@lru_cache
def get_roadmap_settings() -> RoadmapSettings:
    return RoadmapSettings()

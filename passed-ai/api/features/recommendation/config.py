from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class RecommendationSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    openai_api_key: str | None = Field(default=None, alias="OPENAI_API_KEY")
    model: str = Field(default="gpt-4o-mini", alias="RECOMMENDATION_LLM_MODEL")
    timeout_seconds: float = Field(
        default=120,
        gt=0,
        alias="RECOMMENDATION_LLM_TIMEOUT_SECONDS",
    )
    max_retries: int = Field(
        default=1,
        ge=0,
        le=5,
        alias="RECOMMENDATION_LLM_MAX_RETRIES",
    )


@lru_cache
def get_recommendation_settings() -> RecommendationSettings:
    return RecommendationSettings()

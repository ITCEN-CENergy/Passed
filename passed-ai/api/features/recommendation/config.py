from functools import lru_cache

from pydantic import Field, field_validator
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

    @field_validator("model")
    @classmethod
    def require_gpt_4_family(cls, value: str) -> str:
        if not value.startswith("gpt-4"):
            raise ValueError("RECOMMENDATION_LLM_MODEL must use the gpt-4 family")
        return value


@lru_cache
def get_recommendation_settings() -> RecommendationSettings:
    return RecommendationSettings()

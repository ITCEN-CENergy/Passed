from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class RoadmapSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    generator: Literal["fake", "llm"] = Field(default="llm", alias="ROADMAP_GENERATOR")
    openai_api_key: str | None = Field(default=None, alias="OPENAI_API_KEY")
    model: str = Field(default="gpt-4o", alias="ROADMAP_LLM_MODEL")
    timeout_seconds: float = Field(default=180, gt=0, alias="ROADMAP_LLM_TIMEOUT_SECONDS")
    max_retries: int = Field(default=1, ge=0, le=5, alias="ROADMAP_LLM_MAX_RETRIES")
    resource_search_enabled: bool = Field(
        default=True, alias="ROADMAP_RESOURCE_SEARCH_ENABLED"
    )
    resource_search_timeout_seconds: float = Field(
        default=60, gt=0, alias="ROADMAP_RESOURCE_SEARCH_TIMEOUT_SECONDS"
    )
    resource_search_max_concurrency: int = Field(
        default=6,
        ge=1,
        alias="ROADMAP_RESOURCE_SEARCH_MAX_CONCURRENCY",
    )
    resource_recommendation_limit: int = Field(
        default=3,
        ge=1,
        le=3,
        alias="ROADMAP_RESOURCE_RECOMMENDATION_LIMIT",
    )
    generation_total_timeout_seconds: float = Field(
        default=300, gt=0, alias="ROADMAP_GENERATION_TOTAL_TIMEOUT_SECONDS"
    )
    kmooc_service_key: str | None = Field(default=None, alias="KMOOC_SERVICE_KEY")
    kmooc_course_list_url: str | None = Field(
        default="https://apis.data.go.kr/B552881/kmooc_v2_0/courseList_v2_0",
        alias="KMOOC_COURSE_LIST_URL",
    )
    kakao_rest_api_key: str | None = Field(default=None, alias="KAKAO_REST_API_KEY")
    keenable_search_enabled: bool = Field(
        default=True,
        alias="KEENABLE_SEARCH_ENABLED",
    )
    keenable_mcp_url: str = Field(
        default="https://api.keenable.ai/mcp",
        alias="KEENABLE_MCP_URL",
    )
    keenable_requests_per_second: float = Field(
        default=8,
        gt=0,
        le=10,
        alias="KEENABLE_REQUESTS_PER_SECOND",
    )
    keenable_max_retries: int = Field(
        default=2,
        ge=0,
        le=5,
        alias="KEENABLE_MAX_RETRIES",
    )


@lru_cache
def get_roadmap_settings() -> RoadmapSettings:
    return RoadmapSettings()

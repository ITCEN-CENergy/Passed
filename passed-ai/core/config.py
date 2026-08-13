from functools import lru_cache

from dotenv import load_dotenv
from pydantic_settings import BaseSettings, SettingsConfigDict

load_dotenv()

class Settings(BaseSettings):
    openai_api_key: str | None = None

    model_config = SettingsConfigDict(
        # 실행 위치와 무관하게 passed-ai/.env를 읽는다.
        env_file="../../.env",
        env_file_encoding="utf-8",
        extra="ignore"
    )




@lru_cache
def get_settings() -> Settings:
    """설정을 싱글턴으로 반환한다."""
    return Settings()

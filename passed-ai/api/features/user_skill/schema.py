from pydantic import BaseModel, ConfigDict, Field


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
    )


class UserSkillExtractionRequest(ApiModel):
    user_id: int = Field(gt=0)


class UserSkillExtractionResponse(ApiModel):
    user_id: int
    processed_chunk_count: int = Field(ge=0)
    skill_count: int = Field(ge=0)
    unmapped_count: int = Field(ge=0)
    persisted: bool
    resume_chunks_embedded: int = Field(ge=0)
    cover_letter_chunks_embedded: int = Field(ge=0)

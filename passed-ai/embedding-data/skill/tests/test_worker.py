from types import SimpleNamespace

import pytest

from skill_pipeline import worker


def test_embedding_input_uses_only_name_and_description():
    assert worker.build_embedding_input(" Python ", " 데이터 분석 ") == (
        "스킬명: Python\n설명: 데이터 분석"
    )


@pytest.mark.parametrize("name,description", [("", "설명"), ("이름", " "), (None, "설명")])
def test_embedding_input_rejects_missing_values(name, description):
    with pytest.raises(worker.SkillDataValidationError):
        worker.build_embedding_input(name, description)


def test_create_embeddings_restores_order_and_checks_dimension(monkeypatch):
    settings = SimpleNamespace(embedding_dimension=3, embedding_max_retries=1)
    monkeypatch.setattr(worker, "get_settings", lambda: settings)
    response = SimpleNamespace(
        data=[
            SimpleNamespace(index=1, embedding=[4.0, 5.0, 6.0]),
            SimpleNamespace(index=0, embedding=[1.0, 2.0, 3.0]),
        ],
        usage=SimpleNamespace(prompt_tokens=9),
    )
    monkeypatch.setattr(worker, "_request", lambda client, inputs: response)
    result = worker.create_embeddings(["a", "b"], object())
    assert result.vectors == [[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]
    assert result.prompt_tokens == 9


def test_create_embeddings_rejects_wrong_dimension(monkeypatch):
    settings = SimpleNamespace(embedding_dimension=3, embedding_max_retries=1)
    monkeypatch.setattr(worker, "get_settings", lambda: settings)
    response = SimpleNamespace(
        data=[SimpleNamespace(index=0, embedding=[1.0, 2.0])], usage=None
    )
    monkeypatch.setattr(worker, "_request", lambda client, inputs: response)
    with pytest.raises(worker.InvalidEmbeddingResponseError, match="차원 불일치"):
        worker.create_embeddings(["a"], object())

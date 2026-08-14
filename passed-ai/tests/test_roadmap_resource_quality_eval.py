import pytest

from roadmap_evaluation.resource_quality_eval import select_sample, summarize


def test_select_sample_limits_competencies_resources_and_duplicate_urls() -> None:
    rows = [
        {"competency_name": "Docker", "url": "https://example.com/a"},
        {"competency_name": "Docker", "url": "https://example.com/a/"},
        {"competency_name": "Docker", "url": "https://example.com/b"},
        {"competency_name": "Python", "url": "https://example.com/c"},
        {"competency_name": "FastAPI", "url": "https://example.com/d"},
    ]
    selected = select_sample(rows, competency_limit=2, resources_per_competency=2)
    assert [(row["competency_name"], row["url"]) for row in selected] == [
        ("Docker", "https://example.com/a"),
        ("Docker", "https://example.com/b"),
        ("Python", "https://example.com/c"),
    ]


def test_summarize_calculates_offline_baseline_metrics() -> None:
    rows = [
        {"competency_name": "Docker", "competency_relevance": "2",
         "milestone_relevance": "2", "difficulty_fit": "2", "accessible": "yes",
         "duplicate": "no", "language": "ko", "provider": "K-MOOC"},
        {"competency_name": "Docker", "competency_relevance": "0",
         "milestone_relevance": "0", "difficulty_fit": "0", "accessible": "yes",
         "duplicate": "yes", "language": "en", "provider": "Web"},
    ]
    report = summarize(rows)
    assert report["acceptable_rate"] == 0.5
    assert report["irrelevant_rate"] == 0.5
    assert report["duplicate_rate"] == 0.5
    assert report["acceptable_rate_by_competency"] == {"Docker": 0.5}


def test_summarize_requires_labeled_rows() -> None:
    with pytest.raises(ValueError, match="라벨링된"):
        summarize([{"competency_relevance": ""}])

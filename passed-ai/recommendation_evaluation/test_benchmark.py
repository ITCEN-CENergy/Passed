from __future__ import annotations

import csv
from pathlib import Path
import unittest

from recommendation_evaluation.run_benchmark import evaluate


DATASET = Path(__file__).parent / "data" / "recommendation_benchmark.csv"


def _rows() -> list[dict[str, str]]:
    with DATASET.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


class RecommendationBenchmarkTest(unittest.TestCase):
    def test_fixture_has_ten_profiles_and_thirty_candidates_each(self) -> None:
        rows = _rows()
        self.assertEqual(len(rows), 300)
        self.assertEqual(len({row["case_id"] for row in rows}), 300)
        profiles = {row["profile_id"] for row in rows}
        self.assertEqual(len(profiles), 10)
        self.assertTrue(
            all(sum(row["profile_id"] == profile for row in rows) == 30 for profile in profiles)
        )

    def test_fixture_counts_stay_within_policy_bounds(self) -> None:
        for row in _rows():
            required = int(row["required_skill_count"])
            exact = int(row["exact_required_match_count"])
            self.assertLessEqual(0, exact)
            self.assertLessEqual(exact, required)
            self.assertLessEqual(0, int(row["legacy_added_required_count"]))
            self.assertLessEqual(int(row["legacy_added_required_count"]), required)
            self.assertLessEqual(0, int(row["verified_added_required_count"]))
            self.assertLessEqual(int(row["verified_added_required_count"]), required)
            self.assertIn(int(row["ground_truth_relevance"]), {0, 1, 2})

    def test_hybrid_verification_improves_primary_accuracy_and_safety_metrics(self) -> None:
        aggregate, _, _ = evaluate(_rows())
        metrics = {
            (row["variant"], row["metric"]): row["value"]
            for row in aggregate
        }
        self.assertEqual(metrics[("개선 전", "precision_at_10")], 0.4)
        self.assertEqual(metrics[("개선 후", "precision_at_10")], 0.88)
        self.assertGreater(
            metrics[("개선 후", "ndcg_at_10")],
            metrics[("개선 전", "ndcg_at_10")],
        )
        self.assertLess(
            metrics[("개선 후", "over_recommendation_rate_at_10")],
            metrics[("개선 전", "over_recommendation_rate_at_10")],
        )


if __name__ == "__main__":
    unittest.main()

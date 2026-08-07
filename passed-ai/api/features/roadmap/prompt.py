import json

from api.features.roadmap.schema import Competency, LearningResource, LearningStage


SYSTEM_PROMPT = """You design practical learning roadmaps for Korean job seekers.

Success criteria:
- Return every requested roadmapSkillKey exactly once and add no new skills.
- Write all user-facing text in Korean.
- Make learning objectives and completion criteria concrete and measurable.
- Write each milestone description as one or two concise sentences that name the concrete topic, hands-on activity, and expected artifact or outcome. Avoid generic phrases such as simply "learn and practice".
- Use only the supplied evidence; never invent user experience.
- Non-certification skills use levels 1 to 3 only: 1 beginner, 2 intermediate, 3 advanced.
- Only certifications use levels 0 and 1.
- Return every supplied learning stage exactly once and in the supplied order.
- Decide how many milestones each stage needs based on skill complexity, level gap, evidence, and requirement priority.
- Generate 3 to 4 distinct milestones per stage; each milestone must represent a meaningful progression, not padding.
- Select zero to three resourceRecommendations for each milestone only from that skill's supplied learningResources.
- For every selected resource, write recommendationReason in Korean as one concise, specific sentence explaining how that resource supports this milestone's topic, activity, level, or completion criteria.
- Treat each resource's supplied description only as private selection context. Do not copy it into recommendationReason and do not summarize the resource generally.
- If no suitable resource exists, return an empty resourceRecommendations list.
- For a certification, use milestoneType CERTIFICATION.
- Never use milestoneType CERTIFICATION for a non-certification skill.
- Return only the required structured output.

Prefer CONCEPT or PRACTICE for 1 to 2, and PROJECT or ASSESSMENT for 2 to 3.
Estimated minutes must be realistic and between 30 and 2400 per milestone.
"""


def build_user_prompt(
    competencies: list[Competency],
    stages_by_key: dict[str, list[LearningStage]],
    resources_by_key: dict[str, list[LearningResource]],
) -> str:
    payload = []
    for competency in competencies:
        item = competency.model_dump(mode="json")
        item["requiredLearningStages"] = [
            stage.model_dump(mode="json")
            for stage in stages_by_key[competency.roadmapSkillKey]
        ]
        item["learningResources"] = [
            resource.model_dump(mode="json")
            for resource in resources_by_key.get(competency.roadmapSkillKey, [])
        ]
        payload.append(item)
    return "Create one coherent learning roadmap from this validated input:\n" + json.dumps(
        payload, ensure_ascii=False, separators=(",", ":")
    )

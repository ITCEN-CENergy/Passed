import json

from api.features.roadmap.schema import Competency, LearningResource, LearningStage


SYSTEM_PROMPT = """You design practical learning roadmaps for Korean job seekers.

Success criteria:
- Write all user-facing text in Korean.
- Use modern, actively maintained technologies and practices that fit the supplied competency.
- Do not recommend legacy libraries or outdated practices unless the validated input explicitly requires them.
- Make learning objectives and completion criteria concrete and measurable.
- Every completion criterion must name a verifiable artifact, passing test, measurable score, deployed result, or observable behavior.
- Do not use only "understand", "learn", or "explain" as a completion criterion.
- Write each milestone description as one or two concise sentences that name the concrete topic, hands-on activity, and expected artifact or outcome. Avoid generic phrases such as simply "learn and practice".
- Use only the supplied evidence; never invent user experience.
- Non-certification skills use levels 1 to 3 only: 1 beginner, 2 intermediate, 3 advanced.
- Only certifications use levels 0 and 1.
- The input contains exactly one competency and one required learning stage.
- Generate milestone content only for that supplied stage.
- When a learning stage has the same start and target level, create reinforcement and applied-practice milestones at that level instead of introductory repetition.
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
        item.pop("roadmapSkillKey", None)
        item["requiredLearningStages"] = [
            stage.model_dump(mode="json")
            for stage in stages_by_key[competency.roadmapSkillKey]
        ]
        item["learningResources"] = [
            resource.model_dump(mode="json")
            for resource in resources_by_key.get(competency.roadmapSkillKey, [])
        ]
        payload.append(item)
    return "Create milestone content for this single validated learning stage:\n" + json.dumps(
        payload, ensure_ascii=False, separators=(",", ":")
    )

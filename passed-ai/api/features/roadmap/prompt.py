import json

from api.features.roadmap.schema import Competency, MilestoneSlot


SYSTEM_PROMPT = """You design practical learning roadmaps for Korean job seekers.

Success criteria:
- Return every requested roadmapSkillKey exactly once and add no new skills.
- Write all user-facing text in Korean.
- Make learning objectives and completion criteria concrete and measurable.
- Use only the supplied evidence; never invent user experience.
- Non-certification skills use levels 1 to 3 only: 1 beginner, 2 intermediate, 3 advanced.
- Only certifications use levels 0 and 1.
- Generate exactly one content item for each supplied milestone slot, in slot order.
- For a certification, use milestoneType CERTIFICATION.
- Never use milestoneType CERTIFICATION for a non-certification skill.
- Return only the required structured output.

Prefer CONCEPT or PRACTICE for 1 to 2, and PROJECT or ASSESSMENT for 2 to 3.
Estimated minutes must be realistic and between 30 and 2400 per milestone.
"""


def build_user_prompt(
    competencies: list[Competency], slots_by_key: dict[str, list[MilestoneSlot]]
) -> str:
    payload = []
    for competency in competencies:
        item = competency.model_dump(mode="json")
        item["milestoneSlots"] = [
            slot.model_dump(mode="json") for slot in slots_by_key[competency.roadmapSkillKey]
        ]
        payload.append(item)
    return "Create one coherent learning roadmap from this validated input:\n" + json.dumps(
        payload, ensure_ascii=False, separators=(",", ":")
    )

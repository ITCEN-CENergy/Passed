const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const recommendationPath = '/api/v1/users/recommendations'

export class RecommendationApiError extends Error {
  constructor(message, { code, status } = {}) {
    super(message)
    this.name = 'RecommendationApiError'
    this.code = code
    this.status = status
  }
}

async function request(path, options = {}) {
  const { headers, ...requestOptions } = options
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...requestOptions,
    headers: { Accept: 'application/json', ...headers },
  })
  const body = response.status === 204
    ? undefined
    : await response.json().catch(() => undefined)

  if (!response.ok) {
    throw new RecommendationApiError(
      body?.message ?? '추천 요청을 처리하지 못했습니다.',
      { code: body?.code, status: response.status },
    )
  }
  return body
}

function query(params) {
  const values = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null) values.set(key, String(value))
  })
  return values.toString()
}

/**
 * @typedef {'PENDING'|'PROCESSING'|'COMPLETED'|'FAILED'} RecommendationRunStatus
 * @typedef {'HIGHLY_RECOMMENDED'|'RECOMMENDED'|'CHALLENGING'|'LOW_MATCH'} RecommendationGrade
 * @typedef {'REQUIRED'|'PREFERRED'|'RELATED'} SkillType
 * @typedef {{jobRoleId:number, jobRoleName:string}} RecommendationJobRole
 * @typedef {{industryId:number, industryName:string|null, jobRoles:RecommendationJobRole[]}} RecommendationPreference
 * @typedef {{skillId:number, skillName:string, owned:boolean, isImportant:boolean, matchRate:number}} SkillMatch
 * @typedef {{skillType:SkillType, levelMatchRate:number, ownedCount:number, totalCount:number, skills:SkillMatch[]}} SkillGroup
 * @typedef {{skillId:number, skillName:string, isImportant:boolean}} HighlightedSkill
 */

/**
 * @param {{industryId:number, jobRoleIds:number[]}} payload
 * @param {{signal?:AbortSignal}} options
 * @returns {Promise<{runId:number, status:RecommendationRunStatus, candidatePostingCount:number, requiredQualifiedPostingCount:number, industryId:number, jobRoleIds:number[], startedAt:string}>}
 */
export const createRecommendationRun = (payload, { signal } = {}) =>
  request(`${recommendationPath}/runs`, {
    method: 'POST',
    signal,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })

/**
 * @param {{page?:number, size?:number, signal?:AbortSignal}} params
 */
export const getRecommendationHistory = ({ page = 0, size = 10, signal } = {}) =>
  request(`${recommendationPath}?${query({ page, size })}`, { signal })

/** @param {number} recommendationRunId @param {{signal?:AbortSignal}} options */
export const getRecommendationResult = (
  recommendationRunId,
  { signal } = {},
) => request(
  `${recommendationPath}/${encodeURIComponent(recommendationRunId)}`,
  { signal },
)

/**
 * report.skillGroups는 REQUIRED, PREFERRED, RELATED 세 타입을 항상 모두 포함한다.
 * @param {number} recommendationRunId
 * @param {number} jobRecommendationId
 * @param {{signal?:AbortSignal}} options
 * @returns {Promise<{runId:number, jobRecommendationId:number, rankOrder:number, jobPosting:Object, report:{grade:RecommendationGrade, totalScore:number, reason:string, skillGroups:SkillGroup[], topStrengthSkills:HighlightedSkill[], topGapSkills:HighlightedSkill[]}}>}
 */
export const getRecommendationDetail = (
  recommendationRunId,
  jobRecommendationId,
  { signal } = {},
) => request(
  `${recommendationPath}/${encodeURIComponent(recommendationRunId)}/${encodeURIComponent(jobRecommendationId)}`,
  { signal },
)

/** @param {number} recommendationRunId @param {{signal?:AbortSignal}} options */
export const getRecommendationUserSkills = (
  recommendationRunId,
  { signal } = {},
) => request(
  `${recommendationPath}/${encodeURIComponent(recommendationRunId)}/user-skills`,
  { signal },
)

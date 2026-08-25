import { csrfRequest, httpClient } from '../../../common/api/index.js'

const recommendationPath = '/api/v1/users/recommendations'

export class RecommendationApiError extends Error {
  constructor(message, { code, status } = {}) {
    super(message)
    this.name = 'RecommendationApiError'
    this.code = code
    this.status = status
  }
}

const request = (path, options = {}) => httpClient(path, options)

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
 * @typedef {{runId:number, recommendationType:'MULTIPLE_POSTINGS'|'SINGLE_POSTING', jobRecommendationId:number, rankOrder:number, jobPosting:Object, report:{grade:RecommendationGrade, totalScore:number, reason:string, skillGroups:SkillGroup[], topStrengthSkills:HighlightedSkill[], topGapSkills:HighlightedSkill[]}}} RecommendationDetail
 */

/**
 * @param {{industryId:number, jobRoleIds:number[]}} payload
 * @param {{signal?:AbortSignal}} options
 * @returns {Promise<{runId:number, status:RecommendationRunStatus, candidatePostingCount:number, requiredQualifiedPostingCount:number, industryId:number, jobRoleIds:number[], startedAt:string}>}
 */
export const createRecommendationRun = (payload, { signal } = {}) =>
  csrfRequest(`${recommendationPath}/runs`, {
    method: 'POST',
    signal,
    body: payload,
  })

/** @param {number} jobPostingId @param {{signal?:AbortSignal}} options */
export const createSingleRecommendation = (jobPostingId, { signal } = {}) =>
  csrfRequest(`${recommendationPath}/run`, {
    method: 'POST',
    signal,
    body: { jobPostingId },
  })

/**
 * @param {{page?:number, size?:number, type?:'MULTIPLE_POSTINGS'|'SINGLE_POSTING', status?:RecommendationRunStatus, signal?:AbortSignal}} params
 */
export const getRecommendationHistory = ({ page = 0, size = 10, type, status, signal } = {}) =>
  request(`${recommendationPath}?${query({ page, size, type, status })}`, { signal })

/** @param {number} recommendationRunId @param {{signal?:AbortSignal}} options */
export const getRecommendationResult = (
  recommendationRunId,
  { signal } = {},
) => request(
  `${recommendationPath}/${encodeURIComponent(recommendationRunId)}`,
  { signal },
)

export const getLatestRecommendationResult = ({ signal } = {}) =>
  request(`${recommendationPath}/latest`, { signal })

export const getLatestJobPostingRecommendation = (jobPostingId, { signal } = {}) =>
  request(
    `${recommendationPath}/job-postings/${encodeURIComponent(jobPostingId)}/latest`,
    { signal },
  )

export const getIndustries = ({ signal } = {}) =>
  request('/api/v1/users/preferences/industries', { signal })

export const getJobRoles = (industryId, { signal } = {}) =>
  request(`/api/v1/users/preferences/industries/${encodeURIComponent(industryId)}/job-roles`, { signal })

export const getUserJobPreference = ({ signal } = {}) =>
  request('/api/v1/users/preferences/jobs', { signal })

export const updateUserJobPreference = (payload, { signal } = {}) =>
  csrfRequest('/api/v1/users/preferences/jobs', {
    method: 'POST',
    signal,
    body: payload,
  })

/**
 * report.skillGroups는 REQUIRED, PREFERRED, RELATED 세 타입을 항상 모두 포함한다.
 * @param {number} recommendationRunId
 * @param {number} jobRecommendationId
 * @param {{signal?:AbortSignal}} options
 * @returns {Promise<RecommendationDetail>}
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

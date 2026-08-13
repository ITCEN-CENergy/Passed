import { httpClient } from '../../../common/api/httpClient.js'

const jobPostingPath = '/api/v1/jobPostings'

export const getJobPostings = ({
  page = 0,
  size = 12,
  keyword,
  region,
  industryId,
  jobRoleId,
  companySize,
  matchedOnly,
  signal,
} = {}) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  Object.entries({ keyword, region, industryId, jobRoleId, companySize }).forEach(([key, value]) => {
    if (value) params.set(key, String(value))
  })
  if (matchedOnly) params.set('matchedOnly', 'true')
  return httpClient(`${jobPostingPath}?${params}`, { signal })
}

export const getJobPosting = (jobPostingId, { signal } = {}) =>
  httpClient(`${jobPostingPath}/${encodeURIComponent(jobPostingId)}`, { signal })

export const getJobPostingIndustries = ({ signal } = {}) =>
  httpClient('/api/v1/users/preferences/industries', { signal })

export const getJobPostingRoles = (industryId, { signal } = {}) =>
  httpClient(`/api/v1/users/preferences/industries/${encodeURIComponent(industryId)}/job-roles`, { signal })

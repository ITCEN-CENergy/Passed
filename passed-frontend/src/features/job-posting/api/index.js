import { httpClient } from '../../../common/api/httpClient.js'

const jobPostingPath = '/api/v1/jobPostings'

export const getJobPostings = ({ page = 0, size = 12, signal } = {}) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return httpClient(`${jobPostingPath}?${params}`, { signal })
}

export const getJobPosting = (jobPostingId, { signal } = {}) =>
  httpClient(`${jobPostingPath}/${encodeURIComponent(jobPostingId)}`, { signal })

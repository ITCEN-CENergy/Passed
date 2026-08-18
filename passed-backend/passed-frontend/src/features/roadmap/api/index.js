import { csrfRequest, httpClient } from '../../../common/api/index.js'

export const getRoadmaps = ({ signal } = {}) => httpClient('/api/v1/roadmaps', { signal })
export const generateRoadmap = (jobPostingIds) => csrfRequest('/api/v1/roadmaps/generate', { method: 'POST', body: { jobPostingIds } })
export const getRoadmap = (roadmapId, { signal } = {}) => httpClient(`/api/v1/roadmaps/${roadmapId}`, { signal })
export const deleteRoadmap = (roadmapId) => csrfRequest(`/api/v1/roadmaps/${roadmapId}`, { method: 'DELETE' })
export const updateRoadmapStudyTime = (roadmapId, dailyStudyMinutes) => csrfRequest(`/api/v1/roadmaps/${roadmapId}/study-time`, { method: 'PATCH', body: { dailyStudyMinutes } })
export const changeMilestoneCompletion = (milestoneId, completed) => csrfRequest(`/api/v1/milestones/${milestoneId}/completion`, { method: 'PATCH', body: { completed } })
export const previewRoadmapReplan = (roadmapId) => csrfRequest(`/api/v1/roadmaps/${roadmapId}/replan/preview`, { method: 'POST', body: {} })
export const applyRoadmapReplan = (roadmapId, replanToken) => csrfRequest(`/api/v1/roadmaps/${roadmapId}/replan/apply`, { method: 'POST', body: { replanToken } })

import { csrfRequest, httpClient } from '../../../common/api/index.js'

export const extractUserSkills = ({ signal } = {}) =>
  csrfRequest('/api/v1/skill-extractions', { method: 'POST', signal, body: {} })

export const getSkillExtraction = (extractionId, { signal } = {}) =>
  httpClient(`/api/v1/skill-extractions/${encodeURIComponent(extractionId)}`, { signal })

export const getUserSkills = ({ signal } = {}) =>
  httpClient('/api/v1/users/skills', { signal })

export const updateUserSkillPreferences = (skills) =>
  csrfRequest('/api/v1/users/skills/preferences', { method: 'PUT', body: { skills } })

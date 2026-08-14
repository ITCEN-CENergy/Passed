import { csrfRequest, httpClient } from '../../../common/api/index.js'
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export const getCompanyCoverLetters = ({ page = 0, size = 20, signal } = {}) =>
  httpClient(`/api/v1/company-cover-letters?page=${page}&size=${size}`, { signal })

export const getCompanyCoverLetter = (coverLetterId, { signal } = {}) =>
  httpClient(`/api/v1/company-cover-letters/${coverLetterId}`, { signal })

export const getCoverLetterItemFeedback = (itemId, { signal } = {}) =>
  httpClient(`/api/v1/company-cover-letter-items/${itemId}/feedback`, { signal })

export const generateCoverLetterItemFeedback = (itemId) =>
  csrfRequest(`/api/v1/company-cover-letter-items/${itemId}/feedback`, { method: 'POST' })

export const getCoverLetterOverallFeedback = (coverLetterId, { signal } = {}) =>
  httpClient(`/api/v1/company-cover-letters/${coverLetterId}/feedback`, { signal })

export const generateCoverLetterOverallFeedback = (coverLetterId) =>
  csrfRequest(`/api/v1/company-cover-letters/${coverLetterId}/feedback`, { method: 'POST' })

export const createManualCompanyCoverLetter = (payload) =>
  csrfRequest('/api/v1/company-cover-letters/manual', {
    method: 'POST',
    body: payload,
  })

export const replaceCompanyCoverLetter = (coverLetterId, payload) =>
  csrfRequest(`/api/v1/company-cover-letters/${coverLetterId}`, {
    method: 'PUT',
    body: payload,
  })

export const deleteCompanyCoverLetter = (coverLetterId) =>
  request(`/api/v1/company-cover-letters/${coverLetterId}`, { method: 'DELETE' })

export const getCommonCoverLetterQuestions = ({ signal } = {}) =>
  httpClient('/api/v1/cover-letter-questions', { signal })

export const getCommonCoverLetter = ({ signal } = {}) =>
  httpClient('/api/v1/cover-letters', { signal })

export const createCommonCoverLetter = (body) =>
  csrfRequest('/api/v1/cover-letters', { method: 'POST', body })

export const updateCommonCoverLetter = (body) =>
  csrfRequest('/api/v1/cover-letters', { method: 'PUT', body })
  csrfRequest(`/api/v1/company-cover-letters/${coverLetterId}`, { method: 'DELETE' })

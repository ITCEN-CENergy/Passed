import { csrfRequest, httpClient } from '../../../common/api/index.js'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export class CoverLetterApiError extends Error {
  constructor(message, { code, status } = {}) {
    super(message)
    this.name = 'CoverLetterApiError'
    this.code = code
    this.status = status
  }
}

async function request(path, options = {}) {
  const { headers, ...requestOptions } = options
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...requestOptions,
    headers: {
      Accept: 'application/json',
      ...headers,
    },
  })

  if (response.status === 204) {
    return undefined
  }

  const body = await response.json().catch(() => undefined)

  if (!response.ok) {
    throw new CoverLetterApiError(
      body?.message ?? '자기소개서 요청을 처리하지 못했습니다.',
      { code: body?.code, status: response.status },
    )
  }

  return body
}

export const getCompanyCoverLetters = ({ signal } = {}) =>
  request('/api/v1/company-cover-letters', { signal })

export const getCompanyCoverLetter = (coverLetterId, { signal } = {}) =>
  request(`/api/v1/company-cover-letters/${coverLetterId}`, { signal })

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

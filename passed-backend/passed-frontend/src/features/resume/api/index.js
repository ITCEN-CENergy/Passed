import { csrfRequest, getCsrfToken, httpClient } from '../../../common/api/index.js'

export const getResume = ({ signal } = {}) => httpClient('/api/v1/resumes', { signal })

export const createResume = (body) => csrfRequest('/api/v1/resumes', { method: 'POST', body })

export const updateResume = (body) => csrfRequest('/api/v1/resumes', { method: 'PUT', body })

export const uploadResumePhoto = async (file, { signal } = {}) => {
  const csrf = await getCsrfToken({ signal })
  const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
  const path = '/api/v1/files/resume-photos'
  const url = apiBaseUrl.endsWith('/api') ? `${apiBaseUrl}${path.slice(4)}` : `${apiBaseUrl}${path}`
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(url, {
    method: 'POST',
    body: formData,
    credentials: 'include',
    signal,
    headers: {
      Accept: 'application/json',
      [csrf.headerName || 'X-XSRF-TOKEN']: csrf.token,
    },
  })
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new Error(body?.message || '사진을 업로드하지 못했습니다.')
  return body
}

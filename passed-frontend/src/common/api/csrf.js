import { httpClient } from './httpClient.js'

export const getCsrfToken = ({ signal } = {}) =>
  httpClient('/api/v1/auth/csrf', { signal })

export const csrfRequest = async (path, options = {}) => {
  const csrf = await getCsrfToken({ signal: options.signal })
  return httpClient(path, {
    ...options,
    headers: {
      ...options.headers,
      [csrf.headerName || 'X-XSRF-TOKEN']: csrf.token,
    },
  })
}

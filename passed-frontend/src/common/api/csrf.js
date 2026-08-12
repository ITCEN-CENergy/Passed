import { httpClient } from './httpClient.js'

export const getCsrfToken = () => httpClient('/api/v1/auth/csrf')

export const csrfRequest = async (path, options = {}) => {
  const csrf = await getCsrfToken()
  return httpClient(path, {
    ...options,
    headers: {
      ...options.headers,
      [csrf.headerName || 'X-XSRF-TOKEN']: csrf.token,
    },
  })
}

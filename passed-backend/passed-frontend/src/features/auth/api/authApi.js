import { csrfRequest, httpClient } from '../../../common/api/index.js'

export const login = (credentials) => csrfRequest('/api/v1/auth/login', {
  method: 'POST',
  body: credentials,
})

export const signup = (user) => csrfRequest('/api/v1/auth/signup', {
  method: 'POST',
  body: user,
})

export const getCurrentUser = () => httpClient('/api/v1/auth/me')

export const logout = () => csrfRequest('/api/v1/auth/logout', { method: 'POST' })

export const checkEmail = (email) => httpClient(
  `/api/v1/auth/check-email?email=${encodeURIComponent(email)}`,
)

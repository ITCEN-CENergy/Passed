import { csrfRequest, httpClient } from '../../../common/api/index.js'

export const login = (credentials) => csrfRequest('/api/auth/login', {
  method: 'POST',
  body: credentials,
})

export const signup = (user) => csrfRequest('/api/auth/signup', {
  method: 'POST',
  body: user,
})

export const getCurrentUser = () => httpClient('/api/auth/me')

export const logout = () => csrfRequest('/api/auth/logout', { method: 'POST' })

export const checkEmail = (email) => httpClient(
  `/api/auth/check-email?email=${encodeURIComponent(email)}`,
)

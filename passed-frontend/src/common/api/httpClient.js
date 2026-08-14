import ApiError from './ApiError.js'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

const resolveUrl = (path) => {
  if (!apiBaseUrl) return path
  if (apiBaseUrl.endsWith('/api') && path.startsWith('/api/')) {
    return `${apiBaseUrl}${path.slice(4)}`
  }
  return `${apiBaseUrl}${path}`
}

const parseResponse = async (response) => {
  if (response.status === 204) return null

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json().catch(() => null)
  }
  return response.text()
}

const sendRequest = (path, options = {}) => {
  const { body, headers, ...requestOptions } = options
  return fetch(resolveUrl(path), {
    credentials: 'include',
    ...requestOptions,
    headers: {
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
}

const refreshAccessToken = async () => {
  const csrfResponse = await sendRequest('/api/v1/auth/csrf')
  if (!csrfResponse.ok) return null

  const csrf = await parseResponse(csrfResponse)
  const refreshResponse = await sendRequest('/api/v1/auth/refresh', {
    method: 'POST',
    headers: {
      [csrf.headerName || 'X-XSRF-TOKEN']: csrf.token,
    },
  })
  return refreshResponse.ok ? csrf : null
}

const refreshExcludedPaths = new Set([
  '/api/v1/auth/csrf',
  '/api/v1/auth/refresh',
  '/api/v1/auth/login',
  '/api/v1/auth/signup',
  '/api/v1/auth/logout',
  '/api/v1/auth/check-email',
])

const shouldRefreshAccessToken = (path, status) => {
  if (status !== 401) return false

  // 사용자 조회처럼 인증이 필요한 auth API는 자동 재발급 대상에 포함한다.
  const pathname = path.split('?')[0]
  return !refreshExcludedPaths.has(pathname)
}

export const httpClient = async (path, options = {}) => {
  let response

  try {
    response = await sendRequest(path, options)

    if (shouldRefreshAccessToken(path, response.status)) {
      const csrf = await refreshAccessToken()
      if (csrf) {
        response = await sendRequest(path, {
          ...options,
          headers: {
            ...options.headers,
            [csrf.headerName || 'X-XSRF-TOKEN']: csrf.token,
          },
        })
      }
    }
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new ApiError('서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.')
  }

  const responseBody = await parseResponse(response)
  if (!response.ok) {
    const message = typeof responseBody === 'object' && responseBody?.message
      ? responseBody.message
      : '요청을 처리하지 못했습니다.'
    throw new ApiError(message, {
      code: responseBody?.code,
      status: response.status,
    })
  }

  return responseBody
}

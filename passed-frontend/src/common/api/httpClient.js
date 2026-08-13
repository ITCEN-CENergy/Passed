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

export const httpClient = async (path, options = {}) => {
  const { body, headers, ...requestOptions } = options
  let response

  try {
    response = await fetch(resolveUrl(path), {
      credentials: 'include',
      ...requestOptions,
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...headers,
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    })
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

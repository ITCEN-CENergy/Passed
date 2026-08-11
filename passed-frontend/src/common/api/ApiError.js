export default class ApiError extends Error {
  constructor(message, { code, status } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

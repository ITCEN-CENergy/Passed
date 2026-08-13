import { httpClient } from '../../../common/api/index.js'

export const getMyPage = ({ signal } = {}) =>
  httpClient('/api/v1/users/mypage', { signal })

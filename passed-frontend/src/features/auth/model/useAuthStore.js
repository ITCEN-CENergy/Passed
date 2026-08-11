import { create } from 'zustand'
import { getCurrentUser, logout as logoutRequest } from '../api/authApi.js'

const useAuthStore = create((set, get) => ({
  user: null,
  initialized: false,
  isChecking: true,

  initialize: async () => {
    if (get().initialized) return
    set({ initialized: true, isChecking: true })

    try {
      const user = await getCurrentUser()
      set({ user })
    } catch {
      set({ user: null })
    } finally {
      set({ isChecking: false })
    }
  },

  refreshUser: async () => {
    const user = await getCurrentUser()
    set({ user, isChecking: false })
    return user
  },

  logout: async () => {
    try {
      await logoutRequest()
    } catch {
      // 만료된 세션이어도 클라이언트 인증 상태는 정리합니다.
    } finally {
      set({ user: null, isChecking: false })
    }
  },
}))

export default useAuthStore

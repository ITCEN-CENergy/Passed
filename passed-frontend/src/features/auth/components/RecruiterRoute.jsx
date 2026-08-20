import { useEffect } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import PageLoading from '../../../common/components/PageLoading.jsx'
import useAuthStore from '../model/useAuthStore.js'

const RecruiterRoute = ({ children }) => {
  const location = useLocation()
  const user = useAuthStore((state) => state.user)
  const isChecking = useAuthStore((state) => state.isChecking)
  const initialize = useAuthStore((state) => state.initialize)

  useEffect(() => {
    void initialize()
  }, [initialize])

  if (isChecking) {
    return (
      <PageLoading
        fullPage
        title="접근 권한을 확인하고 있어요"
        description="잠시만 기다려주세요."
      />
    )
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ returnTo: location.pathname }} />
  }

  if (user.role !== 'RECRUITER') {
    return <Navigate to="/job-postings" replace />
  }

  return children
}

export default RecruiterRoute

import { PageLoading } from '../../../common/components/index.js'
import useAuthStore from '../../auth/model/useAuthStore.js'
import RecommendationPage from '../../recommendation/pages/RecommendationPage.jsx'
import GuestLandingPage from './GuestLandingPage.jsx'

const LandingHomePage = () => {
  const user = useAuthStore((state) => state.user)
  const isChecking = useAuthStore((state) => state.isChecking)

  if (isChecking) {
    return <PageLoading title="PASSED를 준비하고 있어요" description="로그인 상태를 확인하고 있습니다." />
  }

  return user ? <RecommendationPage home /> : <GuestLandingPage />
}

export default LandingHomePage

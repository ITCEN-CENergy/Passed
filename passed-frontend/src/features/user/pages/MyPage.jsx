import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import { getMyPage } from '../api/index.js'
import { MyPageActionCard } from '../components/index.js'
import styles from './MyPage.module.css'

const ResumeIcon = () => (
  <svg viewBox="0 0 32 32" fill="none">
    <rect x="6" y="3" width="18" height="26" rx="3" stroke="currentColor" strokeWidth="2" />
    <path d="M11 10h8M11 15h8M11 20h5M23 23l5-5 2 2-5 5-3 1 1-3Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

const CoverLetterIcon = () => (
  <svg viewBox="0 0 32 32" fill="none">
    <rect x="5" y="3" width="20" height="25" rx="3" stroke="currentColor" strokeWidth="2" />
    <path d="M10 10h10M10 15h8M10 20h5M23 22l4-4 2 2-4 4-3 1 1-3Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

const RecommendationIcon = () => (
  <svg viewBox="0 0 32 32" fill="none">
    <path d="M8 8h16l2 4v15H6V12l2-4Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
    <path d="m16 14 1.7 3.4 3.8.6-2.8 2.7.7 3.8-3.4-1.8-3.4 1.8.7-3.8-2.8-2.7 3.8-.6L16 14Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
    <path d="M11 8V5h10v3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
  </svg>
)

const CalendarIcon = () => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none">
    <rect x="4" y="5" width="16" height="15" rx="2" stroke="currentColor" strokeWidth="1.8" />
    <path d="M8 3v4M16 3v4M4 9h16M8 13h3M8 16h5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
  </svg>
)

const formatDate = (value) => {
  if (!value) return '등록 정보 없음'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '등록 정보 없음'
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: 'Asia/Seoul',
  }).format(date).replace(/\. /g, '.').replace(/\.$/, '')
}

const resolveProfileImage = (value) => {
  if (!value || /^(https?:)?\/\//.test(value) || value.startsWith('data:')) return value
  const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
  if (/^https?:\/\//.test(apiBaseUrl)) return `${apiBaseUrl}${value.startsWith('/') ? '' : '/'}${value}`
  return value
}

const MyPage = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState('')
  const [retryCount, setRetryCount] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setError('')
    getMyPage({ signal: controller.signal })
      .then(setProfile)
      .catch((requestError) => {
        if (requestError.name === 'AbortError') return
        if (requestError.status === 401 || requestError.status === 403) {
          navigate('/login', {
            replace: true,
            state: { from: location.pathname, message: '로그인 후 마이페이지를 이용해주세요.' },
          })
          return
        }
        setError(requestError.message || '마이페이지 정보를 불러오지 못했습니다.')
      })
    return () => controller.abort()
  }, [location.pathname, navigate, retryCount])

  if (!profile && !error) {
    return (
      <main className={styles.page}>
        <PageLoading title="마이페이지를 불러오고 있어요" description="사용자 정보를 확인하고 있습니다." />
      </main>
    )
  }

  if (error) {
    return (
      <main className={styles.page}>
        <section className={styles.errorState} role="alert">
          <strong>마이페이지를 불러오지 못했습니다</strong>
          <p>{error}</p>
          <button type="button" onClick={() => setRetryCount((count) => count + 1)}>다시 시도</button>
        </section>
      </main>
    )
  }

  const profileImage = resolveProfileImage(profile.profileImageUrl)
  const initial = profile.name?.trim()?.charAt(0) || 'P'
  const documentsUpdated = profile.recommendationRefreshRequired === true
  const updatedDocumentLabel = location.state?.updatedDocument === 'resume'
    ? '이력서'
    : location.state?.updatedDocument === 'coverLetter'
      ? '자기소개서'
      : '이력서 또는 자기소개서'

  return (
    <main className={styles.page}>
      <div className={styles.overview}>
        <section className={styles.greeting} aria-labelledby="mypage-title">
          <p className={styles.eyebrow}><span /> 마이페이지</p>
          <h1 id="mypage-title">{profile.name}님, 안녕하세요! <span aria-hidden="true">👋</span></h1>
          <p>이력서와 자기소개서를 관리하고,<br />추천 내역을 확인해보세요.</p>
        </section>

        <section className={styles.profileCard} aria-label="내 정보">
          <div className={styles.avatar}>
            {profileImage ? (
              <img src={profileImage} alt={`${profile.name} 프로필`} />
            ) : (
              <span aria-label="프로필 이미지 없음">{initial}</span>
            )}
          </div>
          <div className={styles.profileInfo}>
            <div className={styles.identity}>
              <h2>{profile.name}</h2>
              <p>{profile.email}</p>
            </div>
            <div className={styles.documentDates}>
              <div>
                <span className={styles.calendar}><CalendarIcon /></span>
                <p><span>이력서 최종 수정일</span><strong>{formatDate(profile.resumeUpdatedAt)}</strong></p>
              </div>
              <div>
                <span className={styles.calendar}><CalendarIcon /></span>
                <p><span>자기소개서 최종 수정일</span><strong>{formatDate(profile.coverLetterUpdatedAt)}</strong></p>
              </div>
            </div>
          </div>
        </section>
      </div>

      <nav className={styles.actions} aria-label="마이페이지 메뉴">
        <MyPageActionCard
          title="이력서 수정"
          description="이력서를 최신 정보로 관리하고 업데이트하세요."
          icon={<ResumeIcon />}
          to="/resume?returnTo=mypage"
        />
        <MyPageActionCard
          title="자기소개서 수정"
          description="자기소개서를 수정하고 완성도를 높여보세요."
          icon={<CoverLetterIcon />}
          to="/cover-letter?returnTo=mypage"
          tone="green"
        />
        <MyPageActionCard
          title="추천 내역 보기"
          description="맞춤 공고 추천 내역을 확인해보세요."
          icon={<RecommendationIcon />}
          to="/recommendations"
          tone="purple"
        />
      </nav>

      {documentsUpdated && (
        <section className={styles.recommendationNotice} aria-labelledby="recommendation-notice-title">
          <div className={styles.recommendationIcon} aria-hidden="true">✦</div>
          <div>
            <h2 id="recommendation-notice-title">변경한 내용으로 추천을 새로 받아보세요</h2>
            <p>{updatedDocumentLabel} 수정 내용이 저장되었습니다. 최신 정보를 분석해 맞춤 채용공고를 다시 추천해드릴게요.</p>
          </div>
          <button type="button" onClick={() => navigate('/onboarding/analysis')}>재추천 받기 <span aria-hidden="true">→</span></button>
        </section>
      )}
    </main>
  )
}

export default MyPage

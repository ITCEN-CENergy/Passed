import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import { JobPostingCard, PageState } from '../../job-posting/components/index.js'
import { getUniqueJobPostingImages } from '../../job-posting/utils/jobPostingImages.js'
import { getRecommendationResult } from '../api/index.js'
import { GRADE_LABELS } from '../components/index.js'
import styles from './RecommendationHistoryPage.module.css'

const formatDateTime = (value) => {
  if (!value) return '완료 날짜 정보 없음'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '완료 날짜 정보 없음'
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Seoul',
  }).format(date)
}

const RecommendationRunResultPage = () => {
  const { recommendationRunId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [retryCount, setRetryCount] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setError('')
    setResult(null)
    getRecommendationResult(recommendationRunId, { signal: controller.signal })
      .then(setResult)
      .catch((requestError) => {
        if (requestError.name === 'AbortError') return
        if (requestError.status === 401 || requestError.status === 403) {
          navigate('/login', {
            replace: true,
            state: { returnTo: location.pathname, message: '로그인 후 추천 결과를 확인할 수 있어요.' },
          })
          return
        }
        setError(requestError.message || '추천 결과를 불러오지 못했습니다.')
      })
    return () => controller.abort()
  }, [location.pathname, navigate, recommendationRunId, retryCount])

  if (!result && !error) {
    return <PageLoading fullPage title="추천 결과를 불러오고 있어요" description="추천 당시의 상위 공고를 확인하고 있어요." ariaLabel="추천 결과 불러오는 중" />
  }

  if (error) {
    return (
      <main className={styles.page}>
        <PageState
          title="추천 결과를 불러오지 못했습니다"
          description={error}
          action={<button className={styles.primaryButton} type="button" onClick={() => setRetryCount((count) => count + 1)}>다시 시도</button>}
        />
      </main>
    )
  }

  const recommendations = result.recommendations ?? []
  const images = getUniqueJobPostingImages(recommendations.map((item) => item.jobPosting))
  const preference = result.run.preference
  const roles = preference?.jobRoles?.map((role) => role.jobRoleName ?? role.name).filter(Boolean) ?? []

  return (
    <main className={styles.page}>
      <header className={styles.pageHeader}>
        <div>
          <span>RECOMMENDATION #{result.run.runId}</span>
          <h1>맞춤 채용공고 추천 결과</h1>
          <p>{formatDateTime(result.run.completedAt)}에 완료된 추천 결과입니다.</p>
        </div>
      </header>

      <section className={styles.resultSection}>
        <div className={styles.resultHeader}>
          <div>
            <span>{preference?.industryName || '희망 산업 정보 없음'}</span>
            <h2>역량 매칭 채용공고 BEST {recommendations.length}</h2>
          </div>
          <p>{roles.length ? roles.join(', ') : '희망 직무 정보 없음'}</p>
        </div>

        {recommendations.length ? (
          <div className={styles.resultGrid}>
            {recommendations.map((recommendation, index) => (
              <JobPostingCard
                key={recommendation.jobRecommendationId}
                jobPosting={recommendation.jobPosting}
                image={images[index]}
                recommendation={{ ...recommendation, gradeLabel: GRADE_LABELS[recommendation.grade] ?? recommendation.grade }}
                to={`/recommendations/${result.run.runId}/${recommendation.jobRecommendationId}`}
              />
            ))}
          </div>
        ) : (
          <PageState title="추천된 채용공고가 없습니다" description="이 실행에서는 추천 조건을 충족한 공고를 찾지 못했습니다." />
        )}
      </section>
    </main>
  )
}

export default RecommendationRunResultPage

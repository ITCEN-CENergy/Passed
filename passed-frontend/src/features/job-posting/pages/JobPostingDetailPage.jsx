import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import useAuthStore from '../../auth/model/useAuthStore.js'
import { getJobPosting } from '../api/index.js'
import { getJobPostingImage } from '../utils/jobPostingImages.js'
import { JobPostingDetailContent, PageState } from '../components/index.js'
import {
  createSingleRecommendation,
  getLatestJobPostingRecommendation,
  getRecommendationResult,
} from '../../recommendation/api/index.js'
import styles from './JobPostingPages.module.css'

const JobPostingDetailPage = () => {
  const { jobPostingId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const isCheckingAuth = useAuthStore((state) => state.isChecking)
  const [jobPosting, setJobPosting] = useState(null)
  const [error, setError] = useState('')
  const [matching, setMatching] = useState(false)
  const [checkingHistory, setCheckingHistory] = useState(true)
  const image = location.state?.image || getJobPostingImage(jobPostingId)

  useEffect(() => {
    const controller = new AbortController()
    getJobPosting(jobPostingId, { signal: controller.signal })
      .then(setJobPosting)
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError.message)
      })
    return () => controller.abort()
  }, [jobPostingId])

  useEffect(() => {
    if (isCheckingAuth) return undefined
    if (!user) {
      setCheckingHistory(false)
      return undefined
    }
    const controller = new AbortController()
    getLatestJobPostingRecommendation(jobPostingId, { signal: controller.signal })
      .then((latestRecommendation) => {
        if (controller.signal.aborted) return
        if (!latestRecommendation) {
          setCheckingHistory(false)
          return
        }
        navigate(
          `/recommendations/${latestRecommendation.runId}/${latestRecommendation.jobRecommendationId}`,
          { replace: true, state: { image } },
        )
      })
      .catch((requestError) => {
        if (requestError.name === 'AbortError') return
        // 기존 추천 이력 확인 실패는 일반 공고 상세 조회를 막지 않습니다.
        setCheckingHistory(false)
      })
    return () => controller.abort()
  }, [image, isCheckingAuth, jobPostingId, navigate, user])

  const runMatching = async () => {
    setMatching(true)
    setError('')
    try {
      const run = await createSingleRecommendation(Number(jobPostingId))
      const result = await getRecommendationResult(run.runId)
      const recommendation = result.recommendations?.[0]
      if (!recommendation) throw new Error('추천 결과를 찾을 수 없습니다.')
      navigate(
        `/recommendations/${run.runId}/${recommendation.jobRecommendationId}`,
        { replace: true, state: { image } },
      )
    } catch (requestError) {
      setError(requestError.message)
      setMatching(false)
    }
  }

  if ((!jobPosting || checkingHistory) && !error) return <div className={styles.detailShell}><PageLoading title="공고 정보를 불러오고 있어요" description="공고 내용과 매칭 이력을 확인하고 있어요." /></div>
  if (!jobPosting) return <div className={styles.detailShell}><PageState title="공고를 불러오지 못했습니다" description={error} /></div>
  if (matching) return <div className={styles.detailShell}><PageLoading title="공고와 내 스킬을 매칭하고 있어요" description="보유 역량과 공고의 요구 스킬을 분석하고 있어요." /></div>

  return (
    <div className={styles.detailShell}>
      {error && <p className={styles.inlineError} role="alert">{error}</p>}
      <JobPostingDetailContent
        jobPosting={jobPosting}
        image={image}
        action={user
          ? <button className={styles.primaryButton} type="button" onClick={runMatching} disabled={matching}>{matching ? '매칭 분석 중…' : '내 스킬과 매칭하기'}</button>
          : <Link className={styles.primaryButton} to="/login">로그인 후 매칭하기</Link>}
      />
    </div>
  )
}

export default JobPostingDetailPage

import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import { JobPostingDetailContent, PageState } from '../../job-posting/components/index.js'
import { getJobPostingImage } from '../../job-posting/utils/jobPostingImages.js'
import {
  createSingleRecommendation,
  getRecommendationDetail,
  getRecommendationResult,
} from '../api/index.js'
import { RecommendationJourney, RecommendationReport } from '../components/index.js'
import useRoadmapBasketStore from '../../roadmap/model/useRoadmapBasketStore.js'
import jobStyles from '../../job-posting/pages/JobPostingPages.module.css'

const RecommendationDetailPage = () => {
  const { recommendationRunId, jobRecommendationId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const addRoadmapItem = useRoadmapBasketStore((state) => state.addItem)
  const roadmapItems = useRoadmapBasketStore((state) => state.items)
  const [detail, setDetail] = useState(null)
  const [error, setError] = useState('')
  const [reanalyzing, setReanalyzing] = useState(false)
  const [reanalyzeError, setReanalyzeError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    setDetail(null)
    setError('')
    setReanalyzing(false)
    setReanalyzeError('')
    getRecommendationDetail(recommendationRunId, jobRecommendationId, { signal: controller.signal })
      .then(setDetail)
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError.message)
      })
    return () => controller.abort()
  }, [recommendationRunId, jobRecommendationId])

  if (!detail && !error) return <PageLoading fullPage title="매칭 리포트를 불러오고 있어요" description="공고와 내 역량의 적합도를 확인하고 있어요." ariaLabel="매칭 리포트 불러오는 중" />
  if (!detail) return <div className={jobStyles.detailShell}><PageState title="매칭 리포트를 불러오지 못했습니다" description={error} /></div>

  const image = location.state?.image || getJobPostingImage(detail.jobPosting.jobPostingId)
  const roadmapAdded = roadmapItems.some((item) => item.jobPostingId === Number(detail.jobPosting.jobPostingId))
  const createRoadmap = () => {
    addRoadmapItem(detail.jobPosting)
  }
  const reviewCoverLetter = () => {
    navigate(`/cover-letter-write?jobPostingId=${encodeURIComponent(detail.jobPosting.jobPostingId)}`, {
      state: { jobPostingDetail: detail.jobPosting },
    })
  }
  const reanalyze = async () => {
    setReanalyzing(true)
    setReanalyzeError('')
    try {
      const run = await createSingleRecommendation(Number(detail.jobPosting.jobPostingId))
      const result = await getRecommendationResult(run.runId)
      const recommendation = result.recommendations?.[0]
      if (!recommendation) throw new Error('재분석 결과를 찾을 수 없습니다.')
      setDetail(null)
      setReanalyzing(false)
      navigate(
        `/recommendations/${run.runId}/${recommendation.jobRecommendationId}`,
        { replace: true, state: { image } },
      )
    } catch (requestError) {
      setReanalyzeError(requestError.message || '공고를 재분석하지 못했습니다.')
      setReanalyzing(false)
    }
  }

  if (reanalyzing) return <PageLoading fullPage title="공고를 다시 분석하고 있어요" description="현재 내 스킬을 기준으로 적합도 리포트를 새로 만들고 있어요." ariaLabel="공고 적합도 재분석 중" />

  return (
    <div className={jobStyles.detailShell}>
      {reanalyzeError && <p className={jobStyles.inlineError} role="alert">{reanalyzeError}</p>}
      <JobPostingDetailContent
        jobPosting={detail.jobPosting}
        image={image}
        guidance={<RecommendationJourney
          phase="report"
          compact
          action={detail.recommendationType === 'SINGLE_POSTING'
            ? <button className={jobStyles.primaryButton} type="button" onClick={reanalyze} disabled={reanalyzing}>{reanalyzing ? '재분석 중…' : '적합도 재분석'}</button>
            : null}
        />}
      >
        <RecommendationReport
          report={detail.report}
          onCreateRoadmap={createRoadmap}
          onReviewCoverLetter={reviewCoverLetter}
          roadmapAdded={roadmapAdded}
        />
      </JobPostingDetailContent>
    </div>
  )
}

export default RecommendationDetailPage

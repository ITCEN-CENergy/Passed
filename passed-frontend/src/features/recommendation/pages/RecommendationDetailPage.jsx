import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import { JobPostingDetailContent, PageState } from '../../job-posting/components/index.js'
import { getJobPostingImage } from '../../job-posting/utils/jobPostingImages.js'
import { getRecommendationDetail } from '../api/index.js'
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

  useEffect(() => {
    const controller = new AbortController()
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

  return (
    <div className={jobStyles.detailShell}>
      <JobPostingDetailContent
        jobPosting={detail.jobPosting}
        image={image}
        guidance={<RecommendationJourney phase="report" compact />}
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

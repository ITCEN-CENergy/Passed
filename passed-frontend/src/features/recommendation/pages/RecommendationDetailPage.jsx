import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { JobPostingDetailContent, PageState } from '../../job-posting/components/index.js'
import { getJobPostingImage } from '../../job-posting/utils/jobPostingImages.js'
import { getRecommendationDetail } from '../api/index.js'
import { RecommendationReport } from '../components/index.js'
import useRoadmapBasketStore from '../../roadmap/model/useRoadmapBasketStore.js'
import jobStyles from '../../job-posting/pages/JobPostingPages.module.css'

const RecommendationDetailPage = () => {
  const { recommendationRunId, jobRecommendationId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const addRoadmapItem = useRoadmapBasketStore((state) => state.addItem)
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

  if (!detail && !error) return <div className={jobStyles.detailShell}><PageState loading title="매칭 리포트를 불러오고 있어요" /></div>
  if (!detail) return <div className={jobStyles.detailShell}><PageState title="매칭 리포트를 불러오지 못했습니다" description={error} /></div>

  const image = location.state?.image || getJobPostingImage(detail.jobPosting.jobPostingId)
  const createRoadmap = () => {
    addRoadmapItem(detail.jobPosting)
  }
  const reviewCoverLetter = () => {
    navigate('/cover-letter-write', {
      state: { jobPosting: detail.jobPosting },
    })
  }

  return (
    <div className={jobStyles.detailShell}>
      <button className={jobStyles.backButton} type="button" onClick={() => navigate(-1)}>← 추천 결과로</button>
      <JobPostingDetailContent jobPosting={detail.jobPosting} image={image}>
        <RecommendationReport
          report={detail.report}
          onCreateRoadmap={createRoadmap}
          onReviewCoverLetter={reviewCoverLetter}
        />
      </JobPostingDetailContent>
    </div>
  )
}

export default RecommendationDetailPage

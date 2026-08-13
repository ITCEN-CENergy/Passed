import { useEffect, useState } from 'react'
import { JobPostingCard, PageState } from '../components/index.js'
import { getJobPostings } from '../api/index.js'
import { getJobPostingImage } from '../utils/jobPostingImages.js'
import styles from './JobPostingPages.module.css'

const JobPostingListPage = () => {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [retryCount, setRetryCount] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError('')
    getJobPostings({ page, size: 12, signal: controller.signal })
      .then(setResult)
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, retryCount])

  return (
    <div className={styles.pageShell}>
      <header className={styles.pageHeader}>
        <div>
          <p>JOB OPENINGS</p>
          <h1>채용공고</h1>
          <span>나에게 맞는 새로운 기회를 발견해 보세요.</span>
        </div>
        {result && <strong>총 {result.totalElements.toLocaleString()}개</strong>}
      </header>

      {loading ? (
        <div className={styles.skeletonGrid} aria-label="채용공고 불러오는 중">
          {Array.from({ length: 12 }, (_, index) => <span key={index} />)}
        </div>
      ) : error ? (
        <PageState title="공고를 불러오지 못했습니다" description={error} action={<button className={styles.outlineButton} onClick={() => setRetryCount((value) => value + 1)} type="button">다시 시도</button>} />
      ) : result?.content?.length ? (
        <>
          <section className={styles.jobGrid} aria-label="채용공고 목록">
            {result.content.map((jobPosting, index) => {
              const image = getJobPostingImage(jobPosting.jobPostingId, page * 12 + index)
              return <JobPostingCard key={jobPosting.jobPostingId} jobPosting={jobPosting} image={image} to={`/job-postings/${jobPosting.jobPostingId}`} />
            })}
          </section>
          {result.totalPages > 1 && (
            <nav className={styles.pagination} aria-label="채용공고 페이지">
              <button type="button" onClick={() => setPage((value) => value - 1)} disabled={page === 0}>이전</button>
              <span>{page + 1} / {result.totalPages}</span>
              <button type="button" onClick={() => setPage((value) => value + 1)} disabled={page + 1 >= result.totalPages}>다음</button>
            </nav>
          )}
        </>
      ) : (
        <PageState title="등록된 채용공고가 없습니다" description="새로운 공고가 등록되면 이곳에 표시됩니다." />
      )}
    </div>
  )
}

export default JobPostingListPage

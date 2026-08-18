import { useEffect, useState } from 'react'
import { PageLoading } from '../../../common/components/index.js'
import { JobPostingCard, PageState } from '../components/index.js'
import {
  getJobPostingIndustries,
  getJobPostingRoles,
  getJobPostings,
} from '../api/index.js'
import { getJobPostingImage } from '../utils/jobPostingImages.js'
import styles from './JobPostingPages.module.css'

const JobPostingListPage = () => {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [retryCount, setRetryCount] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [region, setRegion] = useState('')
  const [industryId, setIndustryId] = useState('')
  const [jobRoleId, setJobRoleId] = useState('')
  const [companySize, setCompanySize] = useState('')
  const [matchedOnly, setMatchedOnly] = useState(false)
  const [industries, setIndustries] = useState([])
  const [jobRoles, setJobRoles] = useState([])

  useEffect(() => {
    const controller = new AbortController()
    getJobPostingIndustries({ signal: controller.signal })
      .then((response) => setIndustries(response.industries ?? []))
      .catch(() => {})
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!industryId) {
      setJobRoles([])
      setJobRoleId('')
      return undefined
    }
    const controller = new AbortController()
    getJobPostingRoles(industryId, { signal: controller.signal })
      .then((response) => setJobRoles(response.jobRoles ?? []))
      .catch(() => setJobRoles([]))
    return () => controller.abort()
  }, [industryId])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError('')
    getJobPostings({
      page,
      size: 12,
      keyword,
      region,
      industryId,
      jobRoleId,
      companySize,
      matchedOnly,
      signal: controller.signal,
    })
      .then(setResult)
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, retryCount, keyword, region, industryId, jobRoleId, companySize, matchedOnly])

  const applySearch = (event) => {
    event.preventDefault()
    setPage(0)
    setKeyword(searchInput.trim())
  }

  const updateFilter = (setter) => (event) => {
    setPage(0)
    setter(event.target.value)
  }

  return (
    <div className={styles.pageShell}>
      <header className={styles.pageHeader}>
        <div>
          <h1>채용공고</h1>
          <span>나에게 맞는 새로운 기회를 발견해 보세요.</span>
        </div>
        {result && <strong>총 {result.totalElements.toLocaleString()}개</strong>}
      </header>

      <section className={styles.searchPanel} aria-label="채용공고 검색 및 필터">
        <form className={styles.searchBar} onSubmit={applySearch}>
          <span aria-hidden="true">⌕</span>
          <input
            type="search"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="공고 제목이나 내용으로 검색해 보세요"
            aria-label="채용공고 검색어"
          />
          <button type="submit">검색</button>
        </form>
        <div className={styles.filters}>
          <select value={region} onChange={updateFilter(setRegion)} aria-label="지역 필터">
            <option value="">전체 지역</option>
            {['서울', '경기', '인천', '부산', '대전', '대구', '광주', '세종', '울산', '강원', '충청', '전라', '경상', '제주'].map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
          <select value={industryId} onChange={(event) => {
            setPage(0)
            setIndustryId(event.target.value)
            setJobRoleId('')
          }} aria-label="산업 필터">
            <option value="">전체 산업</option>
            {industries.map((industry) => <option key={industry.id} value={industry.id}>{industry.name}</option>)}
          </select>
          <select value={jobRoleId} onChange={updateFilter(setJobRoleId)} disabled={!industryId} aria-label="직무 필터">
            <option value="">전체 직무</option>
            {jobRoles.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
          </select>
          <select value={companySize} onChange={updateFilter(setCompanySize)} aria-label="기업 규모 필터">
            <option value="">전체 기업 규모</option>
            <option value="LARGE_ENTERPRISE">대기업</option>
            <option value="MID_SIZED_ENTERPRISE">중견기업</option>
            <option value="SMALL_AND_MEDIUM_ENTERPRISE">중소기업</option>
            <option value="STARTUP">스타트업</option>
            <option value="PUBLIC_INSTITUTION">공공기관</option>
          </select>
          <button
            className={`${styles.matchFilter} ${matchedOnly ? styles.matchFilterActive : ''}`}
            type="button"
            aria-pressed={matchedOnly}
            onClick={() => {
              setPage(0)
              setMatchedOnly((value) => !value)
            }}
          >
            ✓ 매칭 완료 공고만
          </button>
        </div>
      </section>

      {loading ? (
        <PageLoading title="채용공고를 불러오고 있어요" description="조건에 맞는 공고를 찾고 있어요." />
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

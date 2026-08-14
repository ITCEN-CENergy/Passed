import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import { JobPostingCard, PageState } from '../../job-posting/components/index.js'
import { getUniqueJobPostingImages } from '../../job-posting/utils/jobPostingImages.js'
import {
  getRecommendationHistory,
  getRecommendationResult,
  getRecommendationUserSkills,
} from '../api/index.js'
import { GRADE_LABELS } from '../components/index.js'
import styles from './RecommendationHistoryPage.module.css'

const PAGE_SIZE = 10

const STATUS_LABELS = {
  PENDING: '추천 대기',
  PROCESSING: '추천 진행 중',
  COMPLETED: '추천 완료',
  FAILED: '추천 실패',
}

const CATEGORY_LABELS = {
  TECHNICAL_SKILL: '기술',
  EXPERIENCE: '경험',
  BEHAVIORAL_TRAIT: '역량',
  CERTIFICATION: '자격증',
}

const formatDateTime = (value) => {
  if (!value) return '날짜 정보 없음'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '날짜 정보 없음'
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

const preferenceTitle = (preference) => preference?.industryName || '희망 산업 정보 없음'

const preferenceRoles = (preference) => (
  preference?.jobRoles?.map((role) => role.jobRoleName ?? role.name).filter(Boolean) ?? []
)

const StatusBadge = ({ status }) => (
  <span className={`${styles.statusBadge} ${styles[`status${status}`] ?? ''}`}>
    {STATUS_LABELS[status] ?? status}
  </span>
)

const SkillSnapshot = ({ state }) => {
  if (state.loading) {
    return <div className={styles.skillPanel}><span className={styles.skillLoading}>추천 당시 스킬을 불러오고 있어요…</span></div>
  }
  if (state.error) {
    return <div className={styles.skillPanel}><span className={styles.skillError}>{state.error}</span></div>
  }
  const skills = state.skills ?? []
  return (
    <div className={styles.skillPanel}>
      <div className={styles.skillPanelHeader}>
        <strong>추천 당시 매칭한 내 스킬</strong>
        <span><b>★</b> 중요 표시한 스킬</span>
      </div>
      {skills.length ? (
        <ul className={styles.skillList}>
          {skills.map((skill) => (
            <li key={skill.skillId}>
              <span className={styles.skillName}>{skill.isImportant && <b aria-label="중요 스킬">★</b>}{skill.skillName}</span>
              <span className={styles.skillMeta}>{CATEGORY_LABELS[skill.category] ?? skill.category ?? '기타'} · Lv.{skill.skillLevel}</span>
            </li>
          ))}
        </ul>
      ) : <p className={styles.skillEmpty}>추천 당시 저장된 스킬이 없습니다.</p>}
    </div>
  )
}

const RecommendationHistoryPage = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const [activeTab, setActiveTab] = useState('multiple')
  const [page, setPage] = useState(0)
  const [history, setHistory] = useState(null)
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retryCount, setRetryCount] = useState(0)
  const [openSkillRunId, setOpenSkillRunId] = useState(null)
  const [skillsByRun, setSkillsByRun] = useState({})

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError('')
    setOpenSkillRunId(null)

    const type = activeTab === 'multiple' ? 'MULTIPLE_POSTINGS' : 'SINGLE_POSTING'
    const status = activeTab === 'single' ? 'COMPLETED' : undefined
    getRecommendationHistory({ page, size: PAGE_SIZE, type, status, signal: controller.signal })
      .then(async (response) => {
        const resolvedEntries = activeTab === 'single'
          ? await Promise.all((response.content ?? []).map(async (item) => {
            const result = await getRecommendationResult(item.runId, { signal: controller.signal })
            return { history: item, result }
          }))
          : (response.content ?? []).map((item) => ({ history: item, result: null }))
        if (controller.signal.aborted) return
        setHistory(response)
        setEntries(activeTab === 'single'
          ? resolvedEntries.filter((entry) => entry.result?.recommendations?.length)
          : resolvedEntries)
      })
      .catch((requestError) => {
        if (requestError.name === 'AbortError') return
        if (requestError.status === 401 || requestError.status === 403) {
          navigate('/login', {
            replace: true,
            state: { returnTo: location.pathname, message: '로그인 후 추천 내역을 확인할 수 있어요.' },
          })
          return
        }
        setError(requestError.message || '추천 내역을 불러오지 못했습니다.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [activeTab, location.pathname, navigate, page, retryCount])

  const changeTab = (tab) => {
    if (tab === activeTab) return
    setLoading(true)
    setError('')
    setHistory(null)
    setEntries([])
    setActiveTab(tab)
    setPage(0)
    setOpenSkillRunId(null)
  }

  const toggleSkills = async (runId) => {
    if (openSkillRunId === runId) {
      setOpenSkillRunId(null)
      return
    }
    setOpenSkillRunId(runId)
    if (skillsByRun[runId]) return
    setSkillsByRun((current) => ({ ...current, [runId]: { loading: true } }))
    try {
      const response = await getRecommendationUserSkills(runId)
      setSkillsByRun((current) => ({ ...current, [runId]: { loading: false, skills: response.skills ?? [] } }))
    } catch (requestError) {
      setSkillsByRun((current) => ({
        ...current,
        [runId]: { loading: false, error: requestError.message || '스킬을 불러오지 못했습니다.' },
      }))
    }
  }

  const singleEntries = activeTab === 'single'
    ? entries.filter((entry) => entry?.result?.recommendations?.[0]?.jobPosting)
    : []
  const singleImages = getUniqueJobPostingImages(
    singleEntries.map((entry) => entry.result.recommendations[0].jobPosting),
  )
  const visibleEntries = activeTab === 'single' ? singleEntries : entries

  if (loading) {
    return <main className={styles.page}><PageLoading title="추천 내역을 불러오고 있어요" description="추천 실행 결과를 유형별로 정리하고 있어요." /></main>
  }

  if (error) {
    return (
      <main className={styles.page}>
        <PageState
          title="추천 내역을 불러오지 못했습니다"
          description={error}
          action={<button className={styles.primaryButton} type="button" onClick={() => setRetryCount((count) => count + 1)}>다시 시도</button>}
        />
      </main>
    )
  }

  return (
    <main className={styles.page}>
      <header className={styles.pageHeader}>
        <div>
          <span>MY RECOMMENDATION</span>
          <h1>추천 내역</h1>
          <p>맞춤 추천과 직접 매칭한 공고를 구분해서 확인해 보세요.</p>
        </div>
        <Link to="/recommendations">새로운 채용공고 추천받기</Link>
      </header>

      <div className={styles.tabs} role="tablist" aria-label="추천 내역 유형">
        <button type="button" role="tab" aria-selected={activeTab === 'multiple'} className={activeTab === 'multiple' ? styles.activeTab : ''} onClick={() => changeTab('multiple')}>
          여러 공고 추천
          <span>희망 산업·직무 기반 BEST 12</span>
        </button>
        <button type="button" role="tab" aria-selected={activeTab === 'single'} className={activeTab === 'single' ? styles.activeTab : ''} onClick={() => changeTab('single')}>
          단일 공고 매칭
          <span>직접 선택한 공고 분석</span>
        </button>
      </div>

      <section className={styles.historySection} role="tabpanel">
        <div className={styles.sectionHeading}>
          <div>
            <h2>{activeTab === 'multiple' ? '여러 공고 추천 실행 내역' : '단일 공고 매칭 완료 내역'}</h2>
            <p>{activeTab === 'multiple' ? '실행 내역을 선택하면 당시의 상위 추천 공고를 확인할 수 있어요.' : '공고를 선택하면 나와 채용공고의 적합도 분석으로 이동해요.'}</p>
          </div>
          {history && <span>총 {history.totalElements.toLocaleString()}건</span>}
        </div>

        {visibleEntries.length ? (
          activeTab === 'multiple' ? (
            <div className={styles.historyList}>
              {visibleEntries.map((entry) => {
                const status = entry.history.status
                const roles = preferenceRoles(entry.history.preference)
                const completed = status === 'COMPLETED'
                const dateLabel = completed ? '완료' : status === 'FAILED' ? '종료' : '시작'
                return (
                  <article className={styles.historyCard} key={entry.history.runId}>
                    <div className={styles.historyTop}>
                      <div>
                        <StatusBadge status={status} />
                        <span className={styles.runNumber}>추천 #{entry.history.runId}</span>
                      </div>
                      <span className={styles.date}>{dateLabel} {formatDateTime(entry.history.completedAt ?? entry.history.startedAt)}</span>
                    </div>
                    <h3>{preferenceTitle(entry.history.preference)}</h3>
                    <div className={styles.roleChips}>{roles.length ? roles.map((role) => <span key={role}>{role}</span>) : <span>희망 직무 정보 없음</span>}</div>
                    <div className={styles.historyActions}>
                      <button className={styles.skillButton} type="button" aria-expanded={openSkillRunId === entry.history.runId} onClick={() => toggleSkills(entry.history.runId)}>
                        매칭한 내 스킬 보기 <span aria-hidden="true">{openSkillRunId === entry.history.runId ? '−' : '+'}</span>
                      </button>
                      {completed ? (
                        <Link className={styles.resultButton} to={`/mypage/recommendations/${entry.history.runId}`}>추천 결과 보기</Link>
                      ) : (
                        <button className={styles.resultButton} type="button" disabled>{STATUS_LABELS[status] ?? status}</button>
                      )}
                    </div>
                    {openSkillRunId === entry.history.runId && <SkillSnapshot state={skillsByRun[entry.history.runId] ?? { loading: true }} />}
                  </article>
                )
              })}
            </div>
          ) : (
            <div className={styles.singleGrid}>
              {singleEntries.map((entry, index) => {
                const recommendation = entry.result.recommendations[0]
                return (
                  <article className={styles.singleCard} key={entry.history.runId}>
                    <div className={styles.singleDate}><StatusBadge status="COMPLETED" /><span>{formatDateTime(entry.result?.run?.completedAt ?? entry.history.startedAt)}</span></div>
                    <JobPostingCard
                      jobPosting={recommendation.jobPosting}
                      image={singleImages[index]}
                      recommendation={{ ...recommendation, rankOrder: null, gradeLabel: GRADE_LABELS[recommendation.grade] ?? recommendation.grade }}
                      to={`/recommendations/${entry.history.runId}/${recommendation.jobRecommendationId}`}
                    />
                    <button className={styles.skillButton} type="button" aria-expanded={openSkillRunId === entry.history.runId} onClick={() => toggleSkills(entry.history.runId)}>
                      매칭한 내 스킬 보기 <span aria-hidden="true">{openSkillRunId === entry.history.runId ? '−' : '+'}</span>
                    </button>
                    {openSkillRunId === entry.history.runId && <SkillSnapshot state={skillsByRun[entry.history.runId] ?? { loading: true }} />}
                  </article>
                )
              })}
            </div>
          )
        ) : (
          <PageState
            title={activeTab === 'multiple' ? '여러 공고 추천 내역이 없습니다' : '완료된 단일 공고 매칭 내역이 없습니다'}
            description={activeTab === 'multiple' ? '희망 산업과 직무를 선택해 맞춤 추천을 받아보세요.' : '채용공고 상세에서 내 스킬과 매칭을 진행해 보세요.'}
            action={<Link className={styles.primaryButton} to={activeTab === 'multiple' ? '/recommendations' : '/job-postings'}>{activeTab === 'multiple' ? '추천받기' : '공고 검색하기'}</Link>}
          />
        )}

        {(history?.totalPages ?? 0) > 1 && (
          <nav className={styles.pagination} aria-label="추천 실행 내역 페이지">
            <button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>이전</button>
            <span>{page + 1} / {history.totalPages}</span>
            <button type="button" disabled={page + 1 >= history.totalPages} onClick={() => setPage((value) => value + 1)}>다음</button>
          </nav>
        )}
      </section>

    </main>
  )
}

export default RecommendationHistoryPage

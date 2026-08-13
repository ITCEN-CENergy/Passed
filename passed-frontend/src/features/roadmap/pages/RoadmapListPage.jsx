import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getRoadmaps } from '../api/index.js'
import styles from './RoadmapListPage.module.css'

const STATUS = {
  CREATING: { label: '생성 중', tone: 'creating' },
  ACTIVE: { label: '진행 중', tone: 'active' },
  COMPLETED: { label: '완료', tone: 'completed' },
  FAILED: { label: '생성 실패', tone: 'failed' },
}

const Icon = ({ children }) => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    {children}
  </svg>
)

const icons = {
  clock: <Icon><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></Icon>,
  briefcase: <Icon><rect x="3" y="7" width="18" height="13" rx="2" /><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12h18M10 12v2h4v-2" /></Icon>,
  target: <Icon><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="4" /></Icon>,
  layers: <Icon><path d="m12 3 9 5-9 5-9-5 9-5Z" /><path d="m3 12 9 5 9-5M3 16l9 5 9-5" /></Icon>,
  calendar: <Icon><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M8 3v4M16 3v4M3 10h18" /></Icon>,
  edit: <Icon><path d="m4 20 4.5-1 10-10a2.1 2.1 0 0 0-3-3l-10 10L4 20Z" /><path d="m14 7 3 3" /></Icon>,
}

const formatHours = (minutes, creating) => {
  if (creating && !minutes) return '산정 중'
  const hours = Number(minutes || 0) / 60
  return `${Number.isInteger(hours) ? hours : hours.toFixed(1)}시간`
}

const formatDate = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'Asia/Seoul',
  }).format(date).replace(/\. /g, '.').replace(/\.$/, '')
}

const Metric = ({ icon, children }) => (
  <span className={styles.metric}><span className={styles.metricIcon}>{icons[icon]}</span>{children}</span>
)

const Progress = ({ value, creating }) => {
  const progress = Math.min(100, Math.max(0, Number(value) || 0))
  return (
    <div className={styles.progressBlock}>
      <strong>전체 진행률</strong>
      <div className={styles.progressRing} style={{ '--progress': `${progress * 3.6}deg` }}>
        <div><span>{progress.toFixed(progress % 1 ? 1 : 0)}%</span></div>
      </div>
      {creating && <span className={styles.srOnly}>로드맵 생성 중</span>}
    </div>
  )
}

const RoadmapCard = ({ roadmap }) => {
  const status = STATUS[roadmap.status] ?? { label: roadmap.status, tone: 'failed' }
  const creating = roadmap.status === 'CREATING'
  return (
    <Link className={`${styles.card} ${styles[status.tone]}`} to={`/roadmap/${roadmap.roadmapId}`} aria-label={`${roadmap.title || '로드맵'} 상세 보기`}>
      <div className={styles.cardBody}>
        <div className={styles.titleRow}>
          <h2>{roadmap.title || '로드맵을 생성하고 있어요'}</h2>
          <span className={styles.status}>{status.label}</span>
          {creating && <span className={styles.spinner} aria-hidden="true" />}
        </div>
        <div className={styles.time}><Metric icon="clock">예상 학습시간 {formatHours(roadmap.totalEstimatedMinutes, creating)}</Metric></div>
        <div className={styles.metrics}>
          <Metric icon="briefcase">채용공고 {roadmap.jobPostingCount ?? 0}개</Metric>
          <Metric icon="target">학습 역량 {roadmap.skillCount ?? 0}개</Metric>
          <Metric icon="layers">학습 단계 {roadmap.milestoneCount ?? 0}개</Metric>
        </div>
        <div className={styles.dates}>
          <Metric icon="calendar">생성일 {formatDate(roadmap.createdAt)}</Metric>
          <Metric icon="edit">최근 수정 {formatDate(roadmap.updatedAt)}</Metric>
        </div>
      </div>
      <Progress value={roadmap.progressRate} creating={creating} />
    </Link>
  )
}

const RoadmapListPage = () => {
  const [roadmaps, setRoadmaps] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  const loadRoadmaps = useCallback(async (signal) => {
    try {
      const response = await getRoadmaps({ signal })
      setRoadmaps(Array.isArray(response?.roadmaps) ? response.roadmaps : [])
      setError('')
    } catch (requestError) {
      if (requestError?.name !== 'AbortError') setError(requestError?.message || '로드맵을 불러오지 못했습니다.')
    } finally {
      if (!signal?.aborted) setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void loadRoadmaps(controller.signal)
    return () => controller.abort()
  }, [loadRoadmaps])

  useEffect(() => {
    if (!roadmaps.some((roadmap) => roadmap.status === 'CREATING')) return undefined
    const timer = window.setInterval(() => void loadRoadmaps(), 5000)
    return () => window.clearInterval(timer)
  }, [loadRoadmaps, roadmaps])

  return (
    <section className={styles.page}>
      <div className={styles.heading}>
        <h1>학습 로드맵</h1>
        <p>채용공고에 맞춰 생성한 나만의 학습 계획을 확인해보세요</p>
      </div>

      {isLoading ? (
        <div className={styles.state} role="status"><span className={styles.loader} />로드맵을 불러오고 있어요.</div>
      ) : error ? (
        <div className={styles.state} role="alert"><p>{error}</p><button type="button" onClick={() => { setIsLoading(true); void loadRoadmaps() }}>다시 시도</button></div>
      ) : roadmaps.length === 0 ? (
        <div className={styles.state}><h2>아직 생성된 로드맵이 없어요</h2><p>채용공고를 선택해 맞춤 학습 로드맵을 만들어보세요.</p></div>
      ) : (
        <div className={styles.list}>{roadmaps.map((roadmap) => <RoadmapCard key={roadmap.roadmapId} roadmap={roadmap} />)}</div>
      )}
    </section>
  )
}

export default RoadmapListPage

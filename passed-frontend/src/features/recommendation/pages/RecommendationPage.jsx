import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import coachingBanner from '../../../assets/images/home-banner-coaching-wide.png'
import recommendationBanner from '../../../assets/images/home-banner-recommendation-wide.png'
import { PageLoading } from '../../../common/components/index.js'
import useAuthStore from '../../auth/model/useAuthStore.js'
import { JobPostingCard, PageState } from '../../job-posting/components/index.js'
import { getUniqueJobPostingImages } from '../../job-posting/utils/jobPostingImages.js'
import {
  createRecommendationRun,
  getIndustries,
  getJobRoles,
  getLatestRecommendationResult,
  getRecommendationResult,
  getUserJobPreference,
  updateUserJobPreference,
} from '../api/index.js'
import { GRADE_LABELS, RecommendationJourney } from '../components/index.js'
import styles from './RecommendationPages.module.css'

const matchingSteps = [
  {
    number: '01',
    title: '이력과 역량 분석',
    description: '등록한 이력서와 보유 스킬을 바탕으로 현재 역량을 정리해요.',
  },
  {
    number: '02',
    title: '맞춤 공고 매칭',
    description: '희망 산업과 직무에서 내 역량에 잘 맞는 공고를 선별해요.',
  },
  {
    number: '03',
    title: '합격 준비 연결',
    description: '부족한 역량의 학습 로드맵과 공고별 자기소개서 첨삭으로 이어가요.',
  },
]

const homeBanners = [
  {
    src: recommendationBanner,
    alt: '나에게 맞는 채용공고 추천. 이력서와 자기소개서에서 나의 강점을 찾아 딱 맞는 공고를 추천해요.',
  },
  {
    src: coachingBanner,
    alt: '합격을 위한 취업 코칭. 부족한 역량은 학습 로드맵으로 채우고 자기소개서는 맞춤 첨삭으로 완성해요.',
  },
]

const HomeBannerCarousel = () => {
  const [activeIndex, setActiveIndex] = useState(0)
  const [isPaused, setIsPaused] = useState(false)

  useEffect(() => {
    if (isPaused) return undefined

    const timer = window.setInterval(() => {
      setActiveIndex((currentIndex) => (currentIndex + 1) % homeBanners.length)
    }, 5000)

    return () => window.clearInterval(timer)
  }, [isPaused])

  return (
    <section
      className={styles.hero}
      aria-roledescription="carousel"
      aria-label="PASSED 주요 서비스 소개"
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
      onFocusCapture={() => setIsPaused(true)}
      onBlurCapture={() => setIsPaused(false)}
    >
      <div
        className={styles.heroTrack}
        style={{ transform: `translateX(-${activeIndex * 50}%)` }}
      >
        {homeBanners.map((banner, index) => (
          <div
            className={styles.heroSlide}
            key={banner.src}
            aria-hidden={activeIndex !== index}
          >
            <img
              src={banner.src}
              alt={activeIndex === index ? banner.alt : ''}
              draggable="false"
            />
          </div>
        ))}
      </div>

      <div className={styles.heroDots} aria-label="배너 선택">
        {homeBanners.map((banner, index) => (
          <button
            className={index === activeIndex ? styles.heroDotActive : styles.heroDot}
            type="button"
            key={banner.src}
            aria-label={`${index + 1}번 배너 보기`}
            aria-current={index === activeIndex ? 'true' : undefined}
            onClick={() => setActiveIndex(index)}
          />
        ))}
      </div>
    </section>
  )
}

const GuestRecommendation = () => (
  <div className={styles.guestPanel}>
    <div className={styles.guestCopy}>
      <span className={styles.sectionEyebrow}>AI JOB MATCHING</span>
      <h2>로그인하면 내 역량에 맞는 채용공고를 바로 만날 수 있어요</h2>
      <p>
        단순히 공고를 나열하지 않아요. 내 스킬과 희망 직무를 함께 분석해
        지원 우선순위와 보완할 역량까지 알려드려요.
      </p>
      <div className={styles.guestActions}>
        <Link className={styles.primaryLink} to="/login">로그인하고 맞춤 추천받기</Link>
        <Link className={styles.secondaryLink} to="/job-postings">채용공고 먼저 검색하기</Link>
      </div>
    </div>

    <ol className={styles.matchingSteps} aria-label="맞춤 채용공고 추천 과정">
      {matchingSteps.map((step) => (
        <li key={step.number}>
          <span>{step.number}</span>
          <div>
            <h3>{step.title}</h3>
            <p>{step.description}</p>
          </div>
        </li>
      ))}
    </ol>
  </div>
)

const RecommendationContent = ({ embedded = false }) => {
  const [industries, setIndustries] = useState([])
  const [jobRoles, setJobRoles] = useState([])
  const [industryId, setIndustryId] = useState('')
  const [jobRoleId, setJobRoleId] = useState('')
  const [selectedJobRoleIds, setSelectedJobRoleIds] = useState([])
  const [recommendations, setRecommendations] = useState(null)
  const [run, setRun] = useState(null)
  const [loading, setLoading] = useState(false)
  const [initializing, setInitializing] = useState(true)
  const [rolesLoading, setRolesLoading] = useState(false)
  const [error, setError] = useState('')
  const [loadingStage, setLoadingStage] = useState(0)
  const [savedPreference, setSavedPreference] = useState(null)
  const initialRoleIdsRef = useRef([])

  const loadingDescriptions = [
    '희망 산업과 직무에 맞는 채용공고를 필터링하고 있어요',
    '내 역량 스킬에 맞는 채용 공고를 매칭 중이에요',
    '상위 20개의 채용공고로 선별하고 있어요',
  ]

  const onlyKoreanIndustries = (items) => items.filter(
    (industry) => /[가-힣]/.test(industry.name ?? ''),
  )

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      getIndustries({ signal: controller.signal }),
      getUserJobPreference({ signal: controller.signal }),
      getLatestRecommendationResult({ signal: controller.signal }),
    ])
      .then(([industryResponse, preference, latestResult]) => {
        setIndustries(onlyKoreanIndustries(industryResponse.industries ?? []))
        if (preference?.industry && preference.desiredJobs?.length) {
          const preferredRoleIds = preference.desiredJobs
            .slice(0, 3)
            .map((role) => String(role.id))
          initialRoleIdsRef.current = preferredRoleIds
          setIndustryId(String(preference.industry.id))
          setSavedPreference({
            industryId: Number(preference.industry.id),
            jobRoleIds: preference.desiredJobs.map((role) => Number(role.id)),
          })
        }
        if (latestResult) {
          setRun(latestResult.run)
          setRecommendations(latestResult.recommendations ?? [])
        }
      })
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setInitializing(false)
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!loading) {
      setLoadingStage(0)
      return undefined
    }
    const timer = window.setInterval(() => {
      setLoadingStage((stage) => Math.min(stage + 1, loadingDescriptions.length - 1))
    }, 8000)
    return () => window.clearInterval(timer)
  }, [loading, loadingDescriptions.length])

  useEffect(() => {
    if (!industryId) {
      setJobRoles([])
      setJobRoleId('')
      return undefined
    }
    const controller = new AbortController()
    setRolesLoading(true)
    setJobRoleId('')
    getJobRoles(industryId, { signal: controller.signal })
      .then((response) => {
        const roles = response.jobRoles ?? []
        setJobRoles(roles)
        const initialRoleIds = initialRoleIdsRef.current.filter(
          (roleId) => roles.some((role) => String(role.id) === roleId),
        )
        if (initialRoleIds.length) setSelectedJobRoleIds(initialRoleIds)
        initialRoleIdsRef.current = []
      })
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setRolesLoading(false)
      })
    return () => controller.abort()
  }, [industryId])

  const submit = async (event) => {
    event.preventDefault()
    if (!industryId || !selectedJobRoleIds.length) return
    setLoading(true)
    setError('')
    try {
      const payload = {
        industryId: Number(industryId),
        jobRoleIds: selectedJobRoleIds.map(Number),
      }
      const preferenceChanged = savedPreference?.industryId !== payload.industryId
        || savedPreference.jobRoleIds.length !== payload.jobRoleIds.length
        || savedPreference.jobRoleIds.some((id, index) => id !== payload.jobRoleIds[index])
      if (preferenceChanged) {
        await updateUserJobPreference(payload)
        setSavedPreference(payload)
      }
      const created = await createRecommendationRun(payload)
      const result = await getRecommendationResult(created.runId)
      setRun(result.run)
      setRecommendations(result.recommendations ?? [])
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={`${styles.recommendationPage} ${embedded ? styles.embedded : ''}`}>
      {!embedded && (
        <header className={styles.pageHeader}>
          <h1>채용공고 추천</h1>
          <span>희망하는 산업과 직무를 선택하면 보유 스킬에 맞는 공고를 찾아드려요.</span>
        </header>
      )}

      <form className={styles.preferenceBar} onSubmit={submit}>
        <label>
          <span>희망 산업</span>
          <select value={industryId} onChange={(event) => {
            initialRoleIdsRef.current = []
            setIndustryId(event.target.value)
            setSelectedJobRoleIds([])
          }} disabled={initializing} required>
            <option value="">{initializing ? '설정을 불러오는 중…' : '산업을 선택해 주세요'}</option>
            {industries.map((industry) => <option key={industry.id} value={industry.id}>{industry.name}</option>)}
          </select>
        </label>
        <label>
          <span>희망 직무</span>
          <select value={jobRoleId} onChange={(event) => {
            const selectedId = event.target.value
            setJobRoleId('')
            if (!selectedId || selectedJobRoleIds.includes(selectedId) || selectedJobRoleIds.length >= 3) return
            setSelectedJobRoleIds((ids) => [...ids, selectedId])
          }} disabled={!industryId || rolesLoading || selectedJobRoleIds.length >= 3}>
            <option value="">{rolesLoading ? '직무를 불러오는 중…' : selectedJobRoleIds.length >= 3 ? '최대 3개까지 선택할 수 있어요' : '직무를 선택해 주세요'}</option>
            {jobRoles.map((role) => <option key={role.id} value={role.id} disabled={selectedJobRoleIds.includes(String(role.id))}>{role.name}</option>)}
          </select>
        </label>
        <button type="submit" disabled={!industryId || !selectedJobRoleIds.length || loading || initializing}>{loading ? '추천 중…' : '맞춤형 채용공고 추천'}</button>
        {!!selectedJobRoleIds.length && (
          <div className={styles.selectedRoles} aria-label="선택한 희망 직무">
            {selectedJobRoleIds.map((roleId) => {
              const industry = industries.find((item) => String(item.id) === String(industryId))
              const role = jobRoles.find((item) => String(item.id) === roleId)
              return (
                <span key={roleId}>
                  {industry?.name} <b>›</b> {role?.name}
                  <button type="button" aria-label={`${role?.name} 선택 해제`} onClick={() => setSelectedJobRoleIds((ids) => ids.filter((id) => id !== roleId))}>×</button>
                </span>
              )
            })}
          </div>
        )}
      </form>

      {error && <p className={styles.error} role="alert">{error}</p>}

      <section className={styles.results} aria-live="polite">
        {initializing ? (
          <PageLoading title="저장된 추천 설정과 결과를 불러오고 있어요" description="잠시만 기다려주세요." />
        ) : loading ? (
          <PageLoading
            fullPage
            title="맞춤형 채용공고를 추천하는 중이에요"
            description={loadingDescriptions[loadingStage]}
            ariaLabel="맞춤형 채용공고 추천 중"
          />
        ) : recommendations === null ? (
          <div className={styles.introState}>
            <span aria-hidden="true">◎</span>
            <h2>맞춤 공고를 만나보세요</h2>
            <p>상단에서 희망 산업과 직무를 선택하고 추천을 시작해 주세요.</p>
          </div>
        ) : recommendations.length ? (
          <>
            <div className={styles.resultsHeader}>
              <div>
                <h2>역량 매칭 채용공고 BEST 20</h2>
                <p>공고를 선택해 적합도 리포트를 확인하고, 나에게 맞는 취업 코칭을 받아보세요.</p>
              </div>

              {run?.preference && <span>{run.preference.industryName} · {run.preference.jobRoles?.map((role) => role.name).join(', ')}</span>}
            </div>
            <div className={styles.recommendationGrid}>
              {(() => {
                const images = getUniqueJobPostingImages(recommendations.map((item) => item.jobPosting))
                return recommendations.map((recommendation, index) => {
                  const image = images[index]
                  return (
                    <JobPostingCard
                      key={recommendation.jobRecommendationId}
                      jobPosting={recommendation.jobPosting}
                      image={image}
                      recommendation={{ ...recommendation, gradeLabel: GRADE_LABELS[recommendation.grade] ?? recommendation.grade }}
                      to={`/recommendations/${run.runId}/${recommendation.jobRecommendationId}`}
                    />
                  )
                })
              })()}
            </div>
          </>
        ) : (
          <PageState title="조건에 맞는 추천 공고가 없습니다" description="다른 산업이나 직무를 선택해 다시 추천을 받아보세요." />
        )}
      </section>
    </div>
  )
}

const RecommendationPage = ({ home = false }) => {
  const user = useAuthStore((state) => state.user)
  const isChecking = useAuthStore((state) => state.isChecking)

  if (!home) return <RecommendationContent />

  return (
    <div className={styles.home}>
      <HomeBannerCarousel />

      <section className={styles.recommendationSection} aria-label="맞춤 채용공고 추천">
        {isChecking ? (
          <PageLoading
            title="맞춤 채용공고를 준비하고 있어요"
            description="로그인 상태와 최근 추천 결과를 확인하고 있어요."
          />
        ) : user ? (
          <>
            <header className={styles.sectionHeader}>
              <div>
                <h2 id="home-recommendation-title">채용공고 추천</h2>
                <p>저장된 희망 조건으로 추천을 이어보거나, 새로운 조건으로 다시 추천받아 보세요.</p>
              </div>
              <Link to="/job-postings">전체 채용공고 보기</Link>
            </header>
            <RecommendationContent embedded />
          </>
        ) : (
          <GuestRecommendation />
        )}
      </section>
    </div>
  )
}

export default RecommendationPage

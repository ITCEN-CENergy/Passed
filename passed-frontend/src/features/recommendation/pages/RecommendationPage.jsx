import { useEffect, useRef, useState } from 'react'
import { PageLoading } from '../../../common/components/index.js'
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
import { GRADE_LABELS } from '../components/index.js'
import styles from './RecommendationPages.module.css'

const RecommendationPage = () => {
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
    '상위 12개의 채용공고로 선별하고 있어요',
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
    <div className={styles.recommendationPage}>
      <header className={styles.pageHeader}>
        <h1>채용공고 추천</h1>
        <span>희망하는 산업과 직무를 선택하면 보유 스킬에 맞는 공고를 찾아드려요.</span>
      </header>

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
              <div><h2>역량 매칭 채용공고 BEST 12</h2></div>
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

export default RecommendationPage

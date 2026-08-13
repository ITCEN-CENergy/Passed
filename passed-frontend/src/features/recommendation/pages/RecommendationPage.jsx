import { useEffect, useRef, useState } from 'react'
import { JobPostingCard, PageState } from '../../job-posting/components/index.js'
import { getJobPostingImage } from '../../job-posting/utils/jobPostingImages.js'
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
  const [recommendations, setRecommendations] = useState(null)
  const [run, setRun] = useState(null)
  const [loading, setLoading] = useState(false)
  const [initializing, setInitializing] = useState(true)
  const [rolesLoading, setRolesLoading] = useState(false)
  const [error, setError] = useState('')
  const [savedPreference, setSavedPreference] = useState(null)
  const initialRoleIdRef = useRef('')

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
          const preferredRoleId = String(preference.desiredJobs[0].id)
          initialRoleIdRef.current = preferredRoleId
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
    if (!industryId) {
      setJobRoles([])
      setJobRoleId('')
      return undefined
    }
    const controller = new AbortController()
    setRolesLoading(true)
    if (!initialRoleIdRef.current) setJobRoleId('')
    getJobRoles(industryId, { signal: controller.signal })
      .then((response) => {
        const roles = response.jobRoles ?? []
        setJobRoles(roles)
        const initialRoleId = initialRoleIdRef.current
        if (initialRoleId && roles.some((role) => String(role.id) === initialRoleId)) {
          setJobRoleId(initialRoleId)
        }
        initialRoleIdRef.current = ''
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
    if (!industryId || !jobRoleId) return
    setLoading(true)
    setError('')
    try {
      const payload = {
        industryId: Number(industryId),
        jobRoleIds: [Number(jobRoleId)],
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
        <p>PERSONALIZED JOBS</p>
        <h1>채용공고 추천</h1>
        <span>희망하는 산업과 직무를 선택하면 보유 스킬에 맞는 공고를 찾아드려요.</span>
      </header>

      <form className={styles.preferenceBar} onSubmit={submit}>
        <label>
          <span>희망 산업</span>
          <select value={industryId} onChange={(event) => {
            initialRoleIdRef.current = ''
            setIndustryId(event.target.value)
          }} disabled={initializing} required>
            <option value="">{initializing ? '설정을 불러오는 중…' : '산업을 선택해 주세요'}</option>
            {industries.map((industry) => <option key={industry.id} value={industry.id}>{industry.name}</option>)}
          </select>
        </label>
        <label>
          <span>희망 직무</span>
          <select value={jobRoleId} onChange={(event) => setJobRoleId(event.target.value)} disabled={!industryId || rolesLoading} required>
            <option value="">{rolesLoading ? '직무를 불러오는 중…' : '직무를 선택해 주세요'}</option>
            {jobRoles.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
          </select>
        </label>
        <button type="submit" disabled={!industryId || !jobRoleId || loading || initializing}>{loading ? '추천 중…' : '추천하기'}</button>
      </form>

      {error && <p className={styles.error} role="alert">{error}</p>}

      <section className={styles.results} aria-live="polite">
        {initializing ? (
          <PageState loading title="저장된 추천 설정과 결과를 불러오고 있어요" />
        ) : loading ? (
          <PageState loading title="나에게 맞는 공고를 분석하고 있어요" description="보유 스킬과 공고의 자격요건을 꼼꼼히 비교 중입니다." />
        ) : recommendations === null ? (
          <div className={styles.introState}>
            <span aria-hidden="true">◎</span>
            <h2>맞춤 공고를 만나보세요</h2>
            <p>상단에서 희망 산업과 직무를 선택하고 추천을 시작해 주세요.</p>
          </div>
        ) : recommendations.length ? (
          <>
            <div className={styles.resultsHeader}>
              <div><p>추천 분석 완료</p><h2>{recommendations.length}개의 공고를 찾았어요</h2></div>
              {run?.preference && <span>{run.preference.industryName} · {run.preference.jobRoles?.map((role) => role.name).join(', ')}</span>}
            </div>
            <div className={styles.recommendationGrid}>
              {recommendations.map((recommendation, index) => {
                const image = getJobPostingImage(recommendation.jobPosting.jobPostingId, index)
                return (
                  <JobPostingCard
                    key={recommendation.jobRecommendationId}
                    jobPosting={recommendation.jobPosting}
                    image={image}
                    recommendation={{ ...recommendation, gradeLabel: GRADE_LABELS[recommendation.grade] ?? recommendation.grade }}
                    to={`/recommendations/${run.runId}/${recommendation.jobRecommendationId}`}
                  />
                )
              })}
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

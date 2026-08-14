import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import {
  getIndustries,
  getJobRoles,
  updateUserJobPreference,
} from '../../recommendation/api/index.js'
import styles from './JobPreferenceOnboardingPage.module.css'

const MAX_JOB_ROLES = 3

const JobPreferenceOnboardingPage = () => {
  const navigate = useNavigate()
  const [industries, setIndustries] = useState([])
  const [jobRoles, setJobRoles] = useState([])
  const [industryId, setIndustryId] = useState('')
  const [jobRoleIds, setJobRoleIds] = useState([])
  const [openList, setOpenList] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [rolesLoading, setRolesLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getIndustries({ signal: controller.signal })
      .then((response) => setIndustries(response.industries ?? []))
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!industryId) {
      setJobRoles([])
      return undefined
    }
    const controller = new AbortController()
    setRolesLoading(true)
    getJobRoles(industryId, { signal: controller.signal })
      .then((response) => setJobRoles(response.jobRoles ?? []))
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setRolesLoading(false)
      })
    return () => controller.abort()
  }, [industryId])

  const selectedIndustry = industries.find((item) => String(item.id) === industryId)
  const selectedRoles = useMemo(
    () => jobRoleIds.map((id) => jobRoles.find((item) => String(item.id) === id)).filter(Boolean),
    [jobRoleIds, jobRoles],
  )

  const selectIndustry = (id) => {
    setIndustryId(String(id))
    setJobRoleIds([])
    setOpenList('jobs')
    setConfirmed(false)
    setError('')
  }

  const toggleRole = (id) => {
    const value = String(id)
    setError('')
    setJobRoleIds((current) => {
      if (current.includes(value)) return current
      if (current.length >= MAX_JOB_ROLES) {
        setError(`희망 직무는 최대 ${MAX_JOB_ROLES}개까지 선택할 수 있습니다.`)
        return current
      }
      return [...current, value]
    })
    setOpenList('')
  }

  const removeRole = (id) => {
    setJobRoleIds((current) => current.filter((item) => item !== String(id)))
    setConfirmed(false)
  }

  const save = async () => {
    if (!industryId || !jobRoleIds.length) {
      setError('희망 산업과 직무를 선택해주세요.')
      return
    }
    setSaving(true)
    setError('')
    try {
      await updateUserJobPreference({
        industryId: Number(industryId),
        jobRoleIds: jobRoleIds.map(Number),
      })
      setConfirmed(true)
      setOpenList('')
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <main className={styles.page}><PageLoading title="산업 목록을 불러오고 있어요" /></main>
  }

  if (confirmed) {
    return (
      <main className={styles.page}>
        <header className={styles.heading}>
          <h1>희망 산업/직무 확인</h1>
          <p>선택한 산업과 직무를 확인하고 다음 단계로 이동해주세요.</p>
        </header>
        <section className={styles.summary}>
          <div><strong>선택한 희망 산업</strong><span>{selectedIndustry?.name}</span></div>
          <div><strong>선택한 희망 직무</strong><span>{selectedRoles.map((role) => role.name).join(', ')}</span></div>
        </section>
        <section className={styles.confirmed} aria-labelledby="confirmed-title">
          <div className={styles.sectionTitle}>
            <h2 id="confirmed-title">확정된 산업/직무</h2>
            <p>산업 1개 · 직무 {selectedRoles.length}개</p>
          </div>
          <div className={styles.confirmedGrid}>
            {selectedRoles.map((role) => (
              <article key={role.id}>
                <span>산업</span><strong>{selectedIndustry?.name}</strong>
                <span>직무</span><h3>{role.name}</h3>
              </article>
            ))}
          </div>
        </section>
        <div className={styles.confirmActions}>
          <button className={styles.outlineButton} type="button" onClick={() => setConfirmed(false)}>이전</button>
          <button className={styles.primaryButton} type="button" onClick={() => navigate('/onboarding/resume')}>다음</button>
        </div>
      </main>
    )
  }

  return (
    <main className={styles.page}>
      <header className={styles.heading}>
        <h1>희망 산업/직무 선택</h1>
        <p>희망하는 산업 1개와 직무를 최대 3개까지 선택해주세요.</p>
      </header>

      <section className={styles.selectorCard}>
        <div className={`${styles.selectorGroup} ${openList === 'industries' ? styles.openGroup : ''}`}>
          <label>희망 산업</label>
          <button className={styles.selectButton} type="button" aria-expanded={openList === 'industries'} onClick={() => setOpenList((value) => value === 'industries' ? '' : 'industries')}>
            <span className={selectedIndustry ? styles.selectedText : ''}>{selectedIndustry?.name || '산업을 선택해주세요'}</span>
          </button>
          {openList === 'industries' && (
            <div className={styles.optionList} role="listbox" aria-label="산업 목록">
              {industries.map((industry) => (
                <button key={industry.id} type="button" role="option" aria-selected={industryId === String(industry.id)} onClick={() => selectIndustry(industry.id)}>{industry.name}</button>
              ))}
            </div>
          )}
        </div>

        <div className={`${styles.selectorGroup} ${openList === 'jobs' ? styles.openGroup : ''}`}>
          <div className={styles.jobHeading}>
            <label>희망 직무</label>
            {selectedRoles.length > 0 && (
              <div className={styles.selectedRoles} aria-label="선택한 희망 직무">
                {selectedRoles.map((role) => (
                  <span key={role.id}>{role.name}<button type="button" aria-label={`${role.name} 선택 취소`} onClick={() => removeRole(role.id)}>×</button></span>
                ))}
              </div>
            )}
            <small>{jobRoleIds.length}/{MAX_JOB_ROLES}</small>
          </div>
          <button className={styles.selectButton} type="button" disabled={!industryId || rolesLoading} aria-expanded={openList === 'jobs'} onClick={() => setOpenList((value) => value === 'jobs' ? '' : 'jobs')}>
            <span>{rolesLoading ? '직무를 불러오는 중…' : industryId ? '직무를 선택해주세요' : '산업을 먼저 선택해주세요'}</span>
          </button>
          {openList === 'jobs' && industryId && (
            <div className={styles.optionList} role="listbox" aria-label="직무 목록" aria-multiselectable="true">
              {jobRoles.map((role) => (
                <button key={role.id} type="button" role="option" aria-selected={jobRoleIds.includes(String(role.id))} disabled={jobRoleIds.includes(String(role.id))} onClick={() => toggleRole(role.id)}>{role.name}</button>
              ))}
            </div>
          )}
        </div>
      </section>

      {error && <p className={styles.error} role="alert">{error}</p>}
      <button className={styles.saveButton} type="button" disabled={saving || !industryId || !jobRoleIds.length} onClick={save}>{saving ? '저장 중…' : '선택 저장하기'}</button>
    </main>
  )
}

export default JobPreferenceOnboardingPage

import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import { getUserSkills, updateUserSkillPreferences } from '../api/index.js'
import { createRecommendationRun, getUserJobPreference } from '../../recommendation/api/index.js'
import styles from './SkillReviewPage.module.css'

const categories = {
  ALL: '전체', EXPERIENCE: '경험', TECHNICAL_SKILL: '기술', BEHAVIORAL_TRAIT: '성향', CERTIFICATION: '자격증',
}
const fixedLevelCategories = new Set(['BEHAVIORAL_TRAIT', 'CERTIFICATION'])

const Star = ({ selected }) => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill={selected ? 'currentColor' : 'none'}><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9 1.1-6.2L3 9.6l6.2-.9L12 3Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" /></svg>
)

const SkillReviewPage = () => {
  const navigate = useNavigate()
  const [skills, setSkills] = useState([])
  const [filter, setFilter] = useState('ALL')
  const [maxImportantCount, setMaxImportantCount] = useState(3)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [warningOpen, setWarningOpen] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    getUserSkills({ signal: controller.signal })
      .then((response) => { setSkills(response.skills ?? []); setMaxImportantCount(response.maxImportantCount ?? 3) })
      .catch((requestError) => { if (requestError.name !== 'AbortError') setError(requestError.message) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [])

  const importantCount = skills.filter((skill) => skill.isImportantForMatching).length
  const visibleSkills = useMemo(() => filter === 'ALL' ? skills : skills.filter((skill) => skill.category === filter), [filter, skills])
  const counts = useMemo(() => Object.fromEntries(Object.keys(categories).map((category) => [category, category === 'ALL' ? skills.length : skills.filter((skill) => skill.category === category).length])), [skills])

  const toggleImportant = (userSkillId) => {
    setError('')
    setSkills((current) => current.map((skill) => {
      if (skill.userSkillId !== userSkillId) return skill
      if (!skill.isImportantForMatching && importantCount >= maxImportantCount) {
        setError(`강조 스킬은 최대 ${maxImportantCount}개까지 선택할 수 있습니다.`)
        return skill
      }
      return { ...skill, isImportantForMatching: !skill.isImportantForMatching }
    }))
  }

  const setLevel = (userSkillId, level) => setSkills((current) => current.map((skill) => skill.userSkillId === userSkillId ? { ...skill, level } : skill))

  const submit = async () => {
    if (importantCount < 3) { setWarningOpen(true); return }
    setSaving(true); setError('')
    try {
      await updateUserSkillPreferences(skills.map((skill) => ({ userSkillId: skill.userSkillId, level: Number(skill.level), isImportantForMatching: skill.isImportantForMatching })))
      const preference = await getUserJobPreference()
      const industryId = Number(preference?.industry?.id)
      const jobRoleIds = (preference?.desiredJobs ?? []).map((role) => Number(role.id)).filter(Number.isFinite)
      if (!Number.isFinite(industryId) || !jobRoleIds.length) {
        throw new Error('저장된 희망 산업과 직무를 찾을 수 없습니다.')
      }
      await createRecommendationRun({ industryId, jobRoleIds })
      navigate('/recommendations')
    } catch (requestError) { setError(requestError.message) } finally { setSaving(false) }
  }

  if (loading) return <main className={styles.page}><PageLoading title="핵심 스킬을 불러오고 있어요" /></main>

  return (
    <main className={styles.page}>
      <header className={styles.heading}>
        <div><h1>나의 핵심 스킬</h1><p>별표를 눌러 강조하고 싶은 스킬을 선택하고, 기술과 경험의 레벨을 조정해주세요.</p></div>
        <aside><strong>기술·경험 레벨</strong><span><b>레벨 1</b> 학습, 연습, 기본 사용</span><span><b>레벨 2</b> 실제 경험이나 프로젝트에서 독립적으로 활용</span><span><b>레벨 3</b> 설계, 최적화, 복잡한 문제 해결</span></aside>
        <div className={styles.metrics}><strong>{skills.length}<span>발견된 스킬</span></strong><strong>{importantCount}<span>강조 선택</span></strong></div>
      </header>

      <nav className={styles.filters} aria-label="스킬 카테고리">
        {Object.entries(categories).map(([category, label]) => <button className={filter === category ? styles.activeFilter : ''} type="button" key={category} onClick={() => setFilter(category)}>{label} <b>{counts[category]}</b></button>)}
      </nav>

      {error && <p className={styles.error} role="alert">{error}</p>}
      <section className={styles.grid} aria-label={`${categories[filter]} 스킬`}>
        {visibleSkills.map((skill) => {
          const fixed = fixedLevelCategories.has(skill.category)
          return (
            <article key={skill.userSkillId}>
              <div className={styles.cardTop}><span>{categories[skill.category] ?? skill.category}</span><button type="button" aria-label={`${skill.name} ${skill.isImportantForMatching ? '강조 해제' : '강조 선택'}`} className={skill.isImportantForMatching ? styles.starred : ''} onClick={() => toggleImportant(skill.userSkillId)}><Star selected={skill.isImportantForMatching} /></button></div>
              <h2>{skill.name}</h2>
              {fixed ? <p className={styles.fixedLevel}>레벨 수정이 필요하지 않은 역량입니다.</p> : <div className={styles.levels}><span>레벨</span>{[1, 2, 3].map((level) => <button type="button" className={Number(skill.level) === level ? styles.selectedLevel : ''} key={level} onClick={() => setLevel(skill.userSkillId, level)}>{level}</button>)}</div>}
            </article>
          )
        })}
      </section>
      {!visibleSkills.length && <p className={styles.empty}>이 카테고리에 해당하는 스킬이 없습니다.</p>}
      <button className={styles.submit} type="button" disabled={saving} onClick={submit}>✦ {saving ? '저장 중…' : '맞춤형 채용공고 추천'}</button>

      {warningOpen && <div className={styles.backdrop} role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setWarningOpen(false) }}><section className={styles.dialog} role="alertdialog" aria-modal="true" aria-labelledby="skill-warning-title"><span aria-hidden="true">!</span><h2 id="skill-warning-title">강조 스킬을 확인해주세요</h2><p>강조를 적어도 3개 표시해 주세요.</p><button type="button" autoFocus onClick={() => setWarningOpen(false)}>확인</button></section></div>}
    </main>
  )
}

export default SkillReviewPage

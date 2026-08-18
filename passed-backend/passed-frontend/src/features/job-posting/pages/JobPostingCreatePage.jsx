import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  createJobPosting,
  getJobPostingCreateOptions,
  getJobPostingIndustries,
  getJobPostingRoles,
} from '../api/index.js'
import styles from './JobPostingCreatePage.module.css'

const emptySkill = () => ({ skillId: '', skillLevel: '1' })

const initialForm = {
  title: '',
  companyId: '',
  industryId: '',
  jobRoleId: '',
  startYmd: '',
  endYmd: '',
  headcount: '',
  careerType: '',
  hireType: '',
  region: '',
  educationLevel: '',
  positionDetail: '',
  mainDuty: '',
  qualification: '',
  preference: '',
  disqualification: '',
  process: '',
}

const optionalText = (value) => value.trim() || null
const apiDate = (value) => value ? value.replaceAll('-', '') : null

const AccordionSection = ({ id, title, description, open, onToggle, badge, children }) => (
  <section className={`${styles.section} ${open ? styles.sectionOpen : ''}`}>
    <button
      className={styles.sectionToggle}
      type="button"
      aria-expanded={open}
      aria-controls={`${id}-content`}
      onClick={onToggle}
    >
      <span className={styles.sectionNumber} aria-hidden="true">{id}</span>
      <span className={styles.sectionHeading}>
        <strong>{title}</strong>
        <small>{description}</small>
      </span>
      {badge && <span className={styles.sectionBadge}>{badge}</span>}
      <span className={styles.chevron} aria-hidden="true">⌄</span>
    </button>
    {open && <div id={`${id}-content`} className={styles.sectionContent}>{children}</div>}
  </section>
)

const SkillEditor = ({ title, description, required, skills, options, usedSkillIds, onChange }) => {
  const updateSkill = (index, key, value) => {
    onChange(skills.map((skill, skillIndex) => (
      skillIndex === index ? { ...skill, [key]: value } : skill
    )))
  }

  const removeSkill = (index) => {
    const next = skills.filter((_, skillIndex) => skillIndex !== index)
    onChange(required && next.length === 0 ? [emptySkill()] : next)
  }

  return (
    <div className={styles.skillBlock}>
      <div className={styles.skillHeader}>
        <div>
          <strong>{title}{required && <em>*</em>}</strong>
          <span>{description}</span>
        </div>
        <button type="button" onClick={() => onChange([...skills, emptySkill()])}>+ 기술 추가</button>
      </div>
      {skills.length === 0 ? (
        <button className={styles.emptySkillButton} type="button" onClick={() => onChange([emptySkill()])}>
          우대 기술을 추가해 보세요
        </button>
      ) : (
        <div className={styles.skillRows}>
          {skills.map((skill, index) => (
            <div className={styles.skillRow} key={`${title}-${index}`}>
              <label>
                <span>기술명</span>
                <select
                  value={skill.skillId}
                  onChange={(event) => updateSkill(index, 'skillId', event.target.value)}
                  aria-label={`${title} ${index + 1} 기술명`}
                >
                  <option value="">기술을 선택해 주세요</option>
                  {options.map((option) => (
                    <option
                      key={option.id}
                      value={option.id}
                      disabled={usedSkillIds.has(String(option.id)) && String(option.id) !== String(skill.skillId)}
                    >
                      {option.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>요구 수준</span>
                <select
                  value={skill.skillLevel}
                  onChange={(event) => updateSkill(index, 'skillLevel', event.target.value)}
                  aria-label={`${title} ${index + 1} 요구 수준`}
                >
                  <option value="1">1 · 기초</option>
                  <option value="2">2 · 실무</option>
                  <option value="3">3 · 숙련</option>
                </select>
              </label>
              <button className={styles.removeSkill} type="button" onClick={() => removeSkill(index)} aria-label={`${title} ${index + 1} 제거`}>×</button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const JobPostingCreatePage = () => {
  const navigate = useNavigate()
  const [form, setForm] = useState(initialForm)
  const [requiredSkills, setRequiredSkills] = useState([emptySkill()])
  const [preferredSkills, setPreferredSkills] = useState([])
  const [industries, setIndustries] = useState([])
  const [jobRoles, setJobRoles] = useState([])
  const [companies, setCompanies] = useState([])
  const [skills, setSkills] = useState([])
  const [lookupError, setLookupError] = useState('')
  const [createOptionsError, setCreateOptionsError] = useState('')
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [openSections, setOpenSections] = useState({ '01': true, '02': false, '03': true })

  useEffect(() => {
    const controller = new AbortController()
    getJobPostingIndustries({ signal: controller.signal })
      .then((response) => setIndustries(response.industries ?? []))
      .catch((error) => {
        if (error.name !== 'AbortError') setLookupError('산업 목록을 불러오지 못했습니다. 잠시 후 새로고침해 주세요.')
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    getJobPostingCreateOptions({ signal: controller.signal })
      .then((response) => {
        setCompanies(response.companies ?? [])
        setSkills(response.skills ?? [])
      })
      .catch((error) => {
        if (error.name !== 'AbortError') setCreateOptionsError('회사와 기술 목록을 불러오지 못했습니다. 채용 담당자 계정으로 다시 시도해 주세요.')
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!form.industryId) {
      setJobRoles([])
      return undefined
    }
    const controller = new AbortController()
    setLookupError('')
    getJobPostingRoles(form.industryId, { signal: controller.signal })
      .then((response) => setJobRoles(response.jobRoles ?? []))
      .catch((error) => {
        if (error.name !== 'AbortError') setLookupError('직무 목록을 불러오지 못했습니다. 산업을 다시 선택해 주세요.')
      })
    return () => controller.abort()
  }, [form.industryId])

  const basicProgress = useMemo(() => [
    form.title.trim(), form.companyId, form.industryId, form.jobRoleId,
  ].filter(Boolean).length, [form])

  const usedSkillIds = useMemo(() => new Set(
    [...requiredSkills, ...preferredSkills]
      .map((skill) => String(skill.skillId))
      .filter(Boolean),
  ), [requiredSkills, preferredSkills])

  const update = (key) => (event) => {
    const value = event.target.value
    setForm((current) => ({
      ...current,
      [key]: value,
      ...(key === 'industryId' ? { jobRoleId: '' } : {}),
    }))
  }

  const validate = () => {
    if (!form.title.trim() || !form.companyId || !form.industryId || !form.jobRoleId) {
      setOpenSections((current) => ({ ...current, '01': true }))
      return '기본 정보의 필수 항목을 모두 입력해 주세요.'
    }
    if (Number(form.companyId) <= 0 || !Number.isInteger(Number(form.companyId))) {
      return '회사를 다시 선택해 주세요.'
    }
    if (form.headcount && (Number(form.headcount) <= 0 || !Number.isInteger(Number(form.headcount)))) {
      setOpenSections((current) => ({ ...current, '02': true }))
      return '채용 인원은 1 이상의 정수로 입력해 주세요.'
    }
    if (form.startYmd && form.endYmd && form.startYmd > form.endYmd) {
      setOpenSections((current) => ({ ...current, '02': true }))
      return '접수 시작일은 마감일보다 늦을 수 없습니다.'
    }
    if (requiredSkills.some((skill) => !skill.skillId || Number(skill.skillId) <= 0 || !Number.isInteger(Number(skill.skillId)))) {
      setOpenSections((current) => ({ ...current, '03': true }))
      return '필수 기술을 한 개 이상 선택해 주세요.'
    }
    if (preferredSkills.some((skill) => !skill.skillId || Number(skill.skillId) <= 0 || !Number.isInteger(Number(skill.skillId)))) {
      setOpenSections((current) => ({ ...current, '03': true }))
      return '추가한 우대 기술을 선택해 주세요.'
    }
    const skillIds = [...requiredSkills, ...preferredSkills].map((skill) => String(Number(skill.skillId)))
    if (new Set(skillIds).size !== skillIds.length) {
      setOpenSections((current) => ({ ...current, '03': true }))
      return '같은 기술은 필수 기술과 우대 기술을 통틀어 한 번만 등록할 수 있습니다.'
    }
    return ''
  }

  const submit = async (event) => {
    event.preventDefault()
    const validationError = validate()
    if (validationError) {
      setSubmitError(validationError)
      return
    }

    setSubmitting(true)
    setSubmitError('')
    try {
      const response = await createJobPosting({
        title: form.title.trim(),
        companyId: Number(form.companyId),
        jobRoleId: Number(form.jobRoleId),
        startYmd: apiDate(form.startYmd),
        endYmd: apiDate(form.endYmd),
        headcount: form.headcount ? Number(form.headcount) : null,
        careerType: optionalText(form.careerType),
        hireType: optionalText(form.hireType),
        region: optionalText(form.region),
        educationLevel: optionalText(form.educationLevel),
        positionDetail: optionalText(form.positionDetail),
        mainDuty: optionalText(form.mainDuty),
        qualification: optionalText(form.qualification),
        preference: optionalText(form.preference),
        disqualification: optionalText(form.disqualification),
        process: optionalText(form.process),
        requiredSkills: requiredSkills.map((skill) => ({ skillId: Number(skill.skillId), skillLevel: Number(skill.skillLevel) })),
        preferredSkills: preferredSkills.map((skill) => ({ skillId: Number(skill.skillId), skillLevel: Number(skill.skillLevel) })),
      })
      navigate(`/job-postings/${response.jobPostingId}`)
    } catch (error) {
      setSubmitError(error.message || '채용공고를 등록하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const toggleSection = (id) => setOpenSections((current) => ({ ...current, [id]: !current[id] }))

  return (
    <main className={styles.pageShell}>
      <button className={styles.backButton} type="button" onClick={() => navigate('/job-postings')}>← 채용공고 목록으로</button>
      <header className={styles.pageHeader}>
        <span>STEP 01</span>
        <h1>채용공고 정보 입력</h1>
        <p>채용에 필요한 정보를 입력해 주세요. <strong>* 표시는 필수 항목입니다.</strong></p>
      </header>

      <form onSubmit={submit} noValidate>
        {lookupError && <div className={styles.notice} role="status">{lookupError}</div>}
        {createOptionsError && <div className={styles.notice} role="status">{createOptionsError}</div>}
        {submitError && <div className={styles.error} role="alert">{submitError}</div>}

        <div className={styles.sections}>
          <AccordionSection
            id="01"
            title="기본 정보"
            description="공고를 식별하는 필수 정보를 입력해 주세요."
            open={openSections['01']}
            onToggle={() => toggleSection('01')}
            badge={`필수 정보 4개 중 ${basicProgress}개`}
          >
            <div className={styles.formGrid}>
              <label className={styles.fullField}>
                <span>공고 제목 <em>*</em></span>
                <input value={form.title} onChange={update('title')} maxLength="255" placeholder="예: 백엔드 개발자 채용" required />
              </label>
              <label>
                <span>회사명 <em>*</em></span>
                <select value={form.companyId} onChange={update('companyId')} disabled={companies.length === 0} required>
                  <option value="">회사를 선택해 주세요</option>
                  {companies.map((company) => <option key={company.id} value={company.id}>{company.name}</option>)}
                </select>
              </label>
              <label>
                <span>산업 <em>*</em></span>
                <select value={form.industryId} onChange={update('industryId')} required>
                  <option value="">산업을 선택해 주세요</option>
                  {industries.map((industry) => <option key={industry.id} value={industry.id}>{industry.name}</option>)}
                </select>
              </label>
              <label className={styles.fullField}>
                <span>직무 <em>*</em></span>
                <select value={form.jobRoleId} onChange={update('jobRoleId')} disabled={!form.industryId} required>
                  <option value="">{form.industryId ? '직무를 선택해 주세요' : '산업을 먼저 선택해 주세요'}</option>
                  {jobRoles.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
                </select>
              </label>
            </div>
          </AccordionSection>

          <AccordionSection
            id="02"
            title="채용 조건"
            description="접수 기간과 근무 조건을 입력해 주세요."
            open={openSections['02']}
            onToggle={() => toggleSection('02')}
            badge="선택 입력"
          >
            <div className={styles.formGrid}>
              <label><span>접수 시작일</span><input type="date" value={form.startYmd} onChange={update('startYmd')} /></label>
              <label><span>접수 마감일</span><input type="date" value={form.endYmd} onChange={update('endYmd')} /></label>
              <label><span>채용 인원</span><input type="number" min="1" value={form.headcount} onChange={update('headcount')} placeholder="예: 2" /></label>
              <label><span>경력 조건</span><input value={form.careerType} onChange={update('careerType')} maxLength="50" placeholder="예: 신입·경력 3년 이하" /></label>
              <label><span>고용 형태</span><input value={form.hireType} onChange={update('hireType')} maxLength="255" placeholder="예: 정규직" /></label>
              <label><span>근무 지역</span><input value={form.region} onChange={update('region')} maxLength="255" placeholder="예: 서울 강남구" /></label>
              <label className={styles.fullField}><span>학력 조건</span><input value={form.educationLevel} onChange={update('educationLevel')} maxLength="255" placeholder="예: 학력 무관" /></label>
            </div>
          </AccordionSection>

          <AccordionSection
            id="03"
            title="업무 및 지원 요건"
            description="지원자가 확인할 상세 내용과 기술 요건을 작성해 주세요."
            open={openSections['03']}
            onToggle={() => toggleSection('03')}
            badge="필수 기술 1개 이상"
          >
            <div className={styles.textareaGrid}>
              <label><span>포지션 상세</span><textarea value={form.positionDetail} onChange={update('positionDetail')} placeholder="포지션과 팀을 소개해 주세요." /></label>
              <label><span>주요 업무</span><textarea value={form.mainDuty} onChange={update('mainDuty')} placeholder="담당하게 될 업무를 작성해 주세요." /></label>
              <label><span>자격 요건</span><textarea value={form.qualification} onChange={update('qualification')} placeholder="필수 경험과 역량을 작성해 주세요." /></label>
              <label><span>우대 사항</span><textarea value={form.preference} onChange={update('preference')} placeholder="우대하는 경험과 역량을 작성해 주세요." /></label>
              <label><span>결격 사유</span><textarea value={form.disqualification} onChange={update('disqualification')} placeholder="해당하는 경우 작성해 주세요." /></label>
              <label><span>채용 절차</span><textarea value={form.process} onChange={update('process')} placeholder="예: 서류 → 기술 인터뷰 → 최종 인터뷰" /></label>
            </div>
            <SkillEditor title="필수 기술" description="공고에 반드시 필요한 기술입니다." required skills={requiredSkills} options={skills} usedSkillIds={usedSkillIds} onChange={setRequiredSkills} />
            <SkillEditor title="우대 기술" description="있으면 유리한 기술입니다." skills={preferredSkills} options={skills} usedSkillIds={usedSkillIds} onChange={setPreferredSkills} />
          </AccordionSection>
        </div>

        <div className={styles.actionBar}>
          <span>입력한 내용은 등록 후 채용공고 상세에서 확인할 수 있습니다.</span>
          <div>
            <button className={styles.cancelButton} type="button" onClick={() => navigate('/job-postings')} disabled={submitting}>취소</button>
            <button className={styles.submitButton} type="submit" disabled={submitting}>{submitting ? '등록 중…' : '채용공고 등록'}</button>
          </div>
        </div>
      </form>
    </main>
  )
}

export default JobPostingCreatePage

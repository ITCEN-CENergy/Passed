import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import { createResume, getResume, updateResume, uploadResumePhoto } from '../api/index.js'
import { DynamicResumeSection } from '../components/index.js'
import useAuthStore from '../../auth/model/useAuthStore.js'
import styles from './ResumeEditorPage.module.css'

let clientSequence = 0
const withClientId = (item = {}) => ({ ...item, clientId: item.clientId || `resume-${++clientSequence}` })

const emptyPersonal = { birthDate: '', gender: '', email: '', phone: '', address: '', photoUrl: '' }
const emptyEducation = () => withClientId({ schoolType: '', schoolName: '', admissionDate: '', graduationDate: '', status: '', isTransfer: false, majorName: '', gpa: '', maxGpa: '', otherMajors: '' })

const sections = [
  { key: 'experiences', title: '경력', description: '회사별 경력을 여러 건 추가할 수 있습니다.', empty: { companyName: '', departmentName: '', startDate: '', endDate: '', isWorking: false, position: '', responsibilities: '', salary: '', careerDescription: '' }, fields: [
    { name: 'companyName', label: '회사명', required: true }, { name: 'departmentName', label: '부서명' }, { name: 'position', label: '직급/직책' }, { name: 'startDate', label: '입사일', type: 'date' }, { name: 'endDate', label: '퇴사일', type: 'date' }, { name: 'salary', label: '연봉' }, { name: 'responsibilities', label: '담당 업무', type: 'textarea', wide: true }, { name: 'careerDescription', label: '경력 상세', type: 'textarea', wide: true },
  ] },
  { key: 'activities', title: '인턴/대외활동', description: '프로젝트, 인턴, 동아리 등 주요 경험을 추가해주세요.', empty: { activityType: '', organization: '', startDate: '', endDate: '', description: '' }, fields: [
    { name: 'activityType', label: '활동명', summaryTitle: true }, { name: 'organization', label: '기관/단체명' }, { name: 'startDate', label: '시작일', type: 'date' }, { name: 'endDate', label: '종료일', type: 'date' }, { name: 'description', label: '활동 내용', type: 'textarea', wide: true },
  ] },
  { key: 'trainings', title: '교육', description: '직무와 관련된 교육 이력을 추가해주세요.', empty: { name: '', institution: '', startDate: '', endDate: '', description: '' }, fields: [
    { name: 'name', label: '교육명' }, { name: 'institution', label: '교육기관' }, { name: 'startDate', label: '시작일', type: 'date' }, { name: 'endDate', label: '종료일', type: 'date' }, { name: 'description', label: '교육 내용', type: 'textarea', wide: true },
  ] },
  { key: 'certifications', title: '자격증/어학', description: '보유한 자격증과 어학 성적을 추가해주세요.', empty: { name: '', issuer: '', acquisitionDate: '' }, fields: [
    { name: 'name', label: '자격증명' }, { name: 'issuer', label: '발급기관' }, { name: 'acquisitionDate', label: '취득일', type: 'date' },
  ] },
  { key: 'awards', title: '수상', description: '수상 이력을 추가해주세요.', empty: { name: '', issuer: '', awardDate: '', description: '' }, fields: [
    { name: 'name', label: '수상명' }, { name: 'issuer', label: '수여기관' }, { name: 'awardDate', label: '수상일', type: 'date' }, { name: 'description', label: '수상 내용', type: 'textarea', wide: true },
  ] },
  { key: 'overseasExperiences', title: '해외 경험', description: '해외 체류와 활동 경험을 추가해주세요.', empty: { countryName: '', startDate: '', endDate: '', description: '' }, fields: [
    { name: 'countryName', label: '국가' }, { name: 'startDate', label: '시작일', type: 'date' }, { name: 'endDate', label: '종료일', type: 'date' }, { name: 'description', label: '경험 내용', type: 'textarea', wide: true },
  ] },
  { key: 'languageProficiencies', title: '회화 능력', description: '사용 가능한 외국어와 수준을 추가해주세요.', empty: { languageName: '', proficiencyLevel: '' }, fields: [
    { name: 'languageName', label: '언어', required: true }, { name: 'proficiencyLevel', label: '회화 수준', type: 'select', required: true, options: [{ value: 'DAILY', label: '일상 회화' }, { value: 'BUSINESS', label: '비즈니스 회화' }, { value: 'NATIVE', label: '원어민 수준' }] },
  ] },
]

const initialCollections = Object.fromEntries(sections.map((section) => [section.key, []]))

const parseDate = (value) => {
  if (value === null || value === undefined || value === '') return ''
  const digits = String(value ?? '').replace(/\D/g, '').slice(0, 8)
  if (digits.length !== 8) return null
  const year = Number(digits.slice(0, 4)); const month = Number(digits.slice(4, 6)); const day = Number(digits.slice(6, 8))
  const date = new Date(Date.UTC(year, month - 1, day))
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return null
  return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`
}

const normalizeDate = (value) => parseDate(value) ?? value

const cleanItem = ({ clientId, ...item }) => Object.fromEntries(Object.entries(item).map(([key, value]) => [key, value === '' ? null : key.endsWith('Date') ? normalizeDate(value) : value]))

const hasMeaningfulValue = (item) => Object.entries(item).some(([key, value]) => (
  key !== 'id'
  && key !== 'clientId'
  && value !== null
  && value !== undefined
  && value !== ''
  && value !== false
))

const validateDate = (value, label) => (
  value && parseDate(value) === null ? `${label}은(는) YYYY-MM-DD 형식의 올바른 날짜로 입력해주세요.` : ''
)

const validatePeriod = (startDate, endDate, label) => {
  const start = parseDate(startDate)
  const end = parseDate(endDate)
  if (start && end && end < start) return `${label} 종료일은 시작일보다 빠를 수 없습니다.`
  return ''
}

const validateResume = ({ personalInfo, educations, collections }) => {
  const birthDateError = validateDate(personalInfo.birthDate, '생년월일')
  if (birthDateError) return birthDateError

  for (const [index, education] of educations.entries()) {
    const label = `학력 ${index + 1}번째 항목`
    const dateError = validateDate(education.admissionDate, `${label} 입학일`)
      || validateDate(education.graduationDate, `${label} 졸업일`)
    if (dateError) return dateError

    const periodError = validatePeriod(education.admissionDate, education.graduationDate, label)
    if (periodError) return periodError

    if (education.gpa !== '' && education.gpa !== null
      && education.maxGpa !== '' && education.maxGpa !== null
      && Number(education.gpa) > Number(education.maxGpa)) {
      return `${label}의 학점은 총점을 초과할 수 없습니다.`
    }
  }

  for (const section of sections) {
    const items = collections[section.key].filter(hasMeaningfulValue)
    for (const [index, item] of items.entries()) {
      const label = `${section.title} ${index + 1}번째 항목`
      const missingField = section.fields.find((field) => field.required && !String(item[field.name] ?? '').trim())
      if (missingField) return `${label}의 ${missingField.label}을(를) 입력해주세요.`

      const invalidDateField = section.fields.find((field) => field.type === 'date' && validateDate(item[field.name], field.label))
      if (invalidDateField) return validateDate(item[invalidDateField.name], `${label} ${invalidDateField.label}`)

      const periodError = validatePeriod(item.startDate, item.endDate, label)
      if (periodError) return periodError
    }
  }

  return ''
}

const resolvePhotoUrl = (value) => {
  if (!value || /^(https?:)?\/\//.test(value) || value.startsWith('data:') || value.startsWith('blob:')) return value
  const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
  if (/^https?:\/\//.test(apiBaseUrl)) return `${apiBaseUrl}${value.startsWith('/') ? '' : '/'}${value}`
  return value
}

const DateInput = ({ required = false, value, onChange }) => (
  <input type="text" inputMode="numeric" maxLength="10" required={required} value={value ?? ''} placeholder="YYYY-MM-DD" onChange={(event) => onChange(event.target.value)} onBlur={(event) => onChange(normalizeDate(event.target.value))} />
)

const ResumeEditorPage = ({ onboarding = false }) => {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const [searchParams] = useSearchParams()
  const fileInputRef = useRef(null)
  const initialPayloadRef = useRef('')
  const sectionRefs = useRef({})
  const [personalInfo, setPersonalInfo] = useState(emptyPersonal)
  const [educations, setEducations] = useState([emptyEducation()])
  const [collections, setCollections] = useState(initialCollections)
  const [existing, setExisting] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [photoUploading, setPhotoUploading] = useState(false)
  const [error, setError] = useState('')
  const [editingIds, setEditingIds] = useState(() => new Set())

  const personalComplete = ['birthDate', 'gender', 'email', 'phone', 'address'].every((key) => String(personalInfo[key] ?? '').trim())
  const educationComplete = educations.some((education) => String(education.schoolName ?? '').trim())
  const completedOptionalCount = sections.filter((section) => collections[section.key].some(hasMeaningfulValue)).length
  const completedSectionCount = Number(personalComplete) + Number(educationComplete) + completedOptionalCount
  const completionPercent = completedSectionCount === 9 ? 100 : completedSectionCount * 11

  useEffect(() => {
    const controller = new AbortController()
    getResume({ signal: controller.signal })
      .then((resume) => {
        const nextPersonalInfo = { ...emptyPersonal, ...(resume.personalInfo ?? {}) }
        const nextEducations = (resume.educations?.length ? resume.educations : [{}]).map(withClientId)
        const nextCollections = Object.fromEntries(sections.map((section) => [section.key, (resume[section.key] ?? []).map(withClientId)]))
        setExisting(true)
        setPersonalInfo(nextPersonalInfo)
        setEducations(nextEducations)
        setCollections(nextCollections)
        const { id: personalInfoId, ...cleanPersonalInfo } = nextPersonalInfo
        void personalInfoId
        initialPayloadRef.current = JSON.stringify({
          personalInfo: cleanPersonalInfo,
          educations: nextEducations.map(cleanItem),
          ...Object.fromEntries(sections.map((section) => [section.key, nextCollections[section.key].map(cleanItem)])),
        })
      })
      .catch((requestError) => {
        if (requestError.name !== 'AbortError' && requestError.status !== 404) setError(requestError.message)
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [])

  const photoPreview = useMemo(() => resolvePhotoUrl(personalInfo.photoUrl), [personalInfo.photoUrl])
  const updatePersonal = (name, value) => setPersonalInfo((current) => ({ ...current, [name]: value }))
  const updateEducation = (index, name, value) => setEducations((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, [name]: value } : item))
  const updateCollection = (key, index, name, value) => setCollections((current) => ({ ...current, [key]: current[key].map((item, itemIndex) => itemIndex === index ? { ...item, [name]: value } : item) }))

  const scrollToSection = (key) => window.setTimeout(() => sectionRefs.current[key]?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0)
  const addOptionalSection = (section) => {
    const item = withClientId(section.empty)
    setCollections((current) => ({ ...current, [section.key]: [...current[section.key], item] }))
    setEditingIds((current) => new Set(current).add(item.clientId))
    scrollToSection(section.key)
  }
  const openOptionalSection = (section) => {
    if (collections[section.key].length) scrollToSection(section.key)
    else addOptionalSection(section)
  }
  const removeOptionalItem = (key, index) => {
    const clientId = collections[key][index]?.clientId
    setCollections((current) => ({ ...current, [key]: current[key].filter((_, itemIndex) => itemIndex !== index) }))
    if (clientId) setEditingIds((current) => { const next = new Set(current); next.delete(clientId); return next })
  }

  const selectPhoto = async (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    setPhotoUploading(true); setError('')
    try {
      const result = await uploadResumePhoto(file)
      updatePersonal('photoUrl', result.fileUrl)
    } catch (requestError) { setError(requestError.message) } finally { setPhotoUploading(false) }
  }

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    const cleanedCollections = Object.fromEntries(sections.map((section) => [
      section.key,
      collections[section.key].filter(hasMeaningfulValue),
    ]))
    const validationError = validateResume({ personalInfo, educations, collections: cleanedCollections })
    if (validationError) {
      setError(validationError)
      return
    }

    setSaving(true)
    const { id: personalInfoId, ...cleanPersonalInfo } = personalInfo
    void personalInfoId
    const payload = {
      personalInfo: { ...cleanPersonalInfo, birthDate: normalizeDate(cleanPersonalInfo.birthDate) },
      educations: educations.map(cleanItem),
      ...Object.fromEntries(sections.map((section) => [section.key, cleanedCollections[section.key].map(cleanItem)])),
    }
    const documentChanged = JSON.stringify(payload) !== initialPayloadRef.current
    try {
      if (existing) await updateResume(payload)
      else await createResume(payload)
      if (onboarding) navigate('/onboarding/cover-letter')
      else if (searchParams.get('returnTo') === 'mypage') {
        navigate('/mypage', {
          state: documentChanged ? { documentsUpdated: true, updatedDocument: 'resume' } : undefined,
        })
      } else navigate('/onboarding/cover-letter')
    } catch (requestError) { setError(requestError.message) } finally { setSaving(false) }
  }

  if (loading) return <main className={styles.page}><PageLoading title="이력서 정보를 불러오고 있어요" /></main>

  return (
    <main className={styles.page}>
      <header className={styles.heading}><h1>이력서 {existing ? '수정' : '등록'}</h1><p>필수 항목을 먼저 입력하고 필요한 경력과 경험을 자유롭게 추가해주세요.</p></header>
      <form className={styles.form} onSubmit={submit}>
        <div className={styles.editorLayout}>
          <div className={styles.sections}>
            <section className={styles.requiredSection} ref={(node) => { sectionRefs.current.personal = node }}>
              <header><h2>인적사항 <b>· 필수 입력</b></h2></header>
              <div className={styles.personalLayout}>
                <div className={styles.fieldGrid}>
                  <label><span>생년월일</span><DateInput required value={personalInfo.birthDate} onChange={(value) => updatePersonal('birthDate', value)} /></label>
                  <label><span>성별</span><select required value={personalInfo.gender} onChange={(e) => updatePersonal('gender', e.target.value)}><option value="">선택</option><option value="MALE">남성</option><option value="FEMALE">여성</option><option value="OTHER">기타</option></select></label>
                  <label><span>이메일</span><input type="email" required value={personalInfo.email} placeholder="email@example.com" onChange={(e) => updatePersonal('email', e.target.value)} /></label>
                  <label><span>전화번호</span><input required value={personalInfo.phone} placeholder="010-0000-0000" onChange={(e) => updatePersonal('phone', e.target.value)} /></label>
                  <label className={styles.wide}><span>주소</span><input required value={personalInfo.address} placeholder="주소를 입력해주세요" onChange={(e) => updatePersonal('address', e.target.value)} /></label>
                </div>
                <div className={styles.photoBox}>
                  {photoPreview ? <img src={photoPreview} alt="이력서 프로필 미리보기" /> : <span>사진을 등록해주세요</span>}
                  <button type="button" disabled={photoUploading} onClick={() => fileInputRef.current?.click()}>{photoUploading ? '업로드 중…' : '사진 등록'}</button>
                  <input ref={fileInputRef} type="file" accept="image/png,image/jpeg,image/webp" onChange={selectPhoto} hidden />
                </div>
              </div>
            </section>

            <section className={styles.requiredSection} ref={(node) => { sectionRefs.current.education = node }}>
              <header><h2>학력 <b>· 필수 입력</b></h2><button type="button" onClick={() => setEducations((items) => [...items, emptyEducation()])}>학력 추가</button></header>
              {educations.map((education, index) => (
                <article className={styles.education} key={education.clientId}>
                  {educations.length > 1 && <button className={styles.remove} type="button" onClick={() => setEducations((items) => items.filter((_, itemIndex) => itemIndex !== index))}>삭제</button>}
                  <div className={styles.fieldGrid}>
                    <label><span>학교 구분</span><select value={education.schoolType ?? ''} onChange={(e) => updateEducation(index, 'schoolType', e.target.value)}><option value="">선택</option><option value="HIGH_SCHOOL">고등학교</option><option value="COLLEGE">대학교</option><option value="GRADUATE_SCHOOL">대학원</option></select></label>
                    <label><span>학교명</span><input required value={education.schoolName ?? ''} onChange={(e) => updateEducation(index, 'schoolName', e.target.value)} /></label>
                    <label><span>전공</span><input value={education.majorName ?? ''} onChange={(e) => updateEducation(index, 'majorName', e.target.value)} /></label>
                    <label><span>입학일</span><DateInput value={education.admissionDate} onChange={(value) => updateEducation(index, 'admissionDate', value)} /></label>
                    <label><span>졸업일</span><DateInput value={education.graduationDate} onChange={(value) => updateEducation(index, 'graduationDate', value)} /></label>
                    <label><span>졸업 상태</span><select value={education.status ?? ''} onChange={(e) => updateEducation(index, 'status', e.target.value)}><option value="">선택</option><option value="GRADUATED">졸업</option><option value="EXPECTED">졸업 예정</option><option value="ENROLLED">재학</option><option value="LEAVE">휴학</option></select></label>
                    <label><span>학점</span><input type="number" min="0" step="0.01" value={education.gpa ?? ''} onChange={(e) => updateEducation(index, 'gpa', e.target.value)} /></label>
                    <label><span>총점</span><input type="number" min="0.01" step="0.01" value={education.maxGpa ?? ''} onChange={(e) => updateEducation(index, 'maxGpa', e.target.value)} /></label>
                  </div>
                </article>
              ))}
            </section>

            {sections.map((section) => collections[section.key].length > 0 && (
              <DynamicResumeSection key={section.key} {...section} items={collections[section.key]} editingIds={editingIds}
                sectionRef={(node) => { sectionRefs.current[section.key] = node }}
                onAdd={() => addOptionalSection(section)}
                onChange={(index, name, value) => updateCollection(section.key, index, name, value)}
                onDone={(clientId) => setEditingIds((current) => { const next = new Set(current); next.delete(clientId); return next })}
                onEdit={(clientId) => setEditingIds((current) => new Set(current).add(clientId))}
                onRemove={(index) => removeOptionalItem(section.key, index)}
              />
            ))}

            {error && <p className={styles.error} role="alert">{error}</p>}
            <button className={styles.submit} type="submit" disabled={saving || photoUploading}>{saving ? '이력서 저장 중…' : existing ? '이력서 수정 완료' : '이력서 저장 및 다음'}</button>
          </div>

          <aside className={styles.sidebar} aria-label="이력서 항목">
            <div className={styles.completionHeading}><h2>이력서 완성도</h2><strong>{completionPercent}%</strong></div>
            <div className={styles.progress} aria-label={`이력서 완성도 ${completionPercent}%`}><span style={{ width: `${completionPercent}%` }} /></div>
            <p className={styles.completionMessage}><b>{user?.name ? `${user.name} 회원님` : '회원님'}</b>의<br />이력서가 {completionPercent}% 완성되었어요!</p>
            <hr />
            <h3>이력서 항목</h3>
            <button className={personalComplete ? styles.completedItem : ''} type="button" onClick={() => scrollToSection('personal')}><span>{personalComplete ? '✓' : ''}</span>인적사항<small>필수</small></button>
            <button className={educationComplete ? styles.completedItem : ''} type="button" onClick={() => scrollToSection('education')}><span>{educationComplete ? '✓' : ''}</span>학력<small>필수</small></button>
            {sections.map((section) => (
              <button className={collections[section.key].some(hasMeaningfulValue) ? styles.completedItem : ''} type="button" key={section.key} onClick={() => openOptionalSection(section)}>
                <span>{collections[section.key].length ? '−' : '+'}</span>{section.title}
              </button>
            ))}
          </aside>
        </div>
      </form>
    </main>
  )
}

export default ResumeEditorPage

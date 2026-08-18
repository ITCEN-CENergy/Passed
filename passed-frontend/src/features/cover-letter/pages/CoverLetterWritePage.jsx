import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  createManualCompanyCoverLetter,
  createCompanyCoverLetter,
  getCompanyCoverLetter,
  replaceCompanyCoverLetter,
} from '../api'
import { getJobPosting } from '../../job-posting/api/index.js'
import {
  formatJobPostingList,
  formatJobPostingParagraph,
} from '../../job-posting/utils/jobPostingText.js'
import styles from './styles/CompanyCoverLetterWrite.module.css'

const MAX_ITEMS = 30

const EMPTY_POSTING = {
  postingTitle: '',
  companyName: '',
  jobRoleName: '',
  positionDetail: '',
  careerType: '',
  hireType: '',
  mainDuty: '',
  qualification: '',
  preference: '',
}

function newItem(displayOrder) {
  return {
    clientKey: `${Date.now()}-${displayOrder}-${Math.random()}`,
    id: null,
    questionText: '',
    answer: '',
    characterLimit: 1000,
    displayOrder,
  }
}

function optionalText(value) {
  const normalized = (value ?? '').trim()
  return normalized || null
}

function toFormPosting(posting = {}, { formatLinkedPosting = false } = {}) {
  return {
    postingTitle: posting.postingTitle ?? posting.title ?? '',
    companyName: posting.companyName ?? '',
    jobRoleName: posting.jobRoleName ?? '',
    positionDetail: formatLinkedPosting
      ? formatJobPostingParagraph(posting.positionDetail)
      : posting.positionDetail ?? '',
    careerType: posting.careerType ?? '',
    hireType: posting.hireType ?? '',
    mainDuty: formatLinkedPosting ? formatJobPostingList(posting.mainDuty) : posting.mainDuty ?? '',
    qualification: formatLinkedPosting ? formatJobPostingList(posting.qualification) : posting.qualification ?? '',
    preference: formatLinkedPosting ? formatJobPostingList(posting.preference) : posting.preference ?? '',
  }
}

function toFormItem(item, index) {
  return {
    clientKey: `saved-${item.id}`,
    id: item.id,
    questionText: item.questionText ?? '',
    answer: item.answer ?? '',
    characterLimit: item.characterLimit ?? '',
    displayOrder: index + 1,
  }
}

const CoverLetterWritePage = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const { coverLetterId: coverLetterIdParam } = useParams()
  const coverLetterId = Number(coverLetterIdParam)
  const isEditMode = coverLetterIdParam !== undefined
  const hasValidId = Number.isSafeInteger(coverLetterId) && coverLetterId > 0
  const stateJobPostingDetail = !isEditMode
    ? location.state?.jobPostingDetail ?? location.state?.jobPosting
    : null
  const stateJobPostingId = Number(stateJobPostingDetail?.jobPostingId)
  const queryJobPostingId = Number(searchParams.get('jobPostingId'))
  const linkedJobPostingId = Number.isSafeInteger(stateJobPostingId) && stateJobPostingId > 0
    ? stateJobPostingId
    : queryJobPostingId
  const hasLinkedJobPosting = Number.isSafeInteger(linkedJobPostingId) && linkedJobPostingId > 0
  const [title, setTitle] = useState('')
  const [posting, setPosting] = useState(() => toFormPosting(
    stateJobPostingDetail ?? EMPTY_POSTING,
    { formatLinkedPosting: Boolean(stateJobPostingDetail) },
  ))
  const [items, setItems] = useState([newItem(1)])
  const [isManual, setIsManual] = useState(!hasLinkedJobPosting)
  const [isLoading, setIsLoading] = useState(isEditMode || (!stateJobPostingDetail && hasLinkedJobPosting))
  const [isSaving, setIsSaving] = useState(false)
  const [loadError, setLoadError] = useState(null)
  const [formError, setFormError] = useState(null)

  useEffect(() => {
    if (!isEditMode) return undefined
    if (!hasValidId) {
      setIsLoading(false)
      return undefined
    }

    const controller = new AbortController()
    setIsLoading(true)
    setLoadError(null)

    getCompanyCoverLetter(coverLetterId, { signal: controller.signal })
      .then((coverLetter) => {
        setTitle(coverLetter.title ?? '')
        setPosting(toFormPosting(coverLetter.jobPosting, {
          formatLinkedPosting: !coverLetter.manual,
        }))
        setItems(coverLetter.items.map(toFormItem))
        setIsManual(Boolean(coverLetter.manual))
      })
      .catch((error) => {
        if (error?.name !== 'AbortError') setLoadError(error)
      })
      .finally(() => setIsLoading(false))

    return () => controller.abort()
  }, [coverLetterId, hasValidId, isEditMode])

  useEffect(() => {
    if (isEditMode || stateJobPostingDetail || !hasLinkedJobPosting) return undefined

    const controller = new AbortController()
    setIsLoading(true)
    setLoadError(null)

    getJobPosting(linkedJobPostingId, { signal: controller.signal })
      .then((jobPostingDetail) => {
        setPosting(toFormPosting(jobPostingDetail, { formatLinkedPosting: true }))
        setIsManual(false)
      })
      .catch((error) => {
        if (error?.name !== 'AbortError') setLoadError(error)
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false)
      })

    return () => controller.abort()
  }, [hasLinkedJobPosting, isEditMode, linkedJobPostingId, stateJobPostingDetail])

  const answerTotal = useMemo(
    () => items.reduce((total, item) => total + item.answer.length, 0),
    [items],
  )

  const updatePosting = (field, value) => {
    setPosting((current) => ({ ...current, [field]: value }))
  }

  const updateItem = (clientKey, field, value) => {
    setItems((current) => current.map((item) => (
      item.clientKey === clientKey ? { ...item, [field]: value } : item
    )))
  }

  const addItem = () => {
    if (items.length >= MAX_ITEMS) return
    setItems((current) => [...current, newItem(current.length + 1)])
  }

  const removeItem = (clientKey) => {
    if (items.length === 1) return
    setItems((current) => current
      .filter((item) => item.clientKey !== clientKey)
      .map((item, index) => ({ ...item, displayOrder: index + 1 })))
  }

  const validate = () => {
    if ((isEditMode || !isManual) && !title.trim()) return '자기소개서 제목을 입력해 주세요.'
    if (title.length > 255) return '자기소개서 제목은 255자 이내로 입력해 주세요.'
    if ((!isEditMode || isManual) && !posting.postingTitle.trim()) return '공고 제목을 입력해 주세요.'
    if ((!isEditMode || isManual) && !posting.jobRoleName.trim()) return '직무를 입력해 주세요.'
    if (items.length === 0 || items.length > MAX_ITEMS) return '문항은 1개 이상 30개 이하로 작성해 주세요.'

    for (const [index, item] of items.entries()) {
      if (!item.questionText.trim()) return `${index + 1}번 문항의 질문을 입력해 주세요.`
      const limit = item.characterLimit === '' ? null : Number(item.characterLimit)
      if (limit !== null && (!Number.isInteger(limit) || limit <= 0)) {
        return `${index + 1}번 문항의 글자 수 제한을 확인해 주세요.`
      }
      if (limit !== null && item.answer.length > limit) {
        return `${index + 1}번 문항의 답변이 ${limit.toLocaleString()}자를 초과했습니다.`
      }
    }
    return null
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    const validationMessage = validate()
    if (validationMessage) {
      setFormError(validationMessage)
      return
    }

    const itemPayload = items.map((item, index) => ({
      ...(isEditMode && item.id ? { id: item.id } : {}),
      questionText: item.questionText.trim(),
      answer: item.answer,
      characterLimit: item.characterLimit === '' ? null : Number(item.characterLimit),
      displayOrder: index + 1,
    }))
    const postingPayload = {
      postingTitle: posting.postingTitle.trim(),
      companyName: optionalText(posting.companyName),
      jobRoleName: posting.jobRoleName.trim(),
      positionDetail: optionalText(posting.positionDetail),
      careerType: optionalText(posting.careerType),
      hireType: optionalText(posting.hireType),
      mainDuty: optionalText(posting.mainDuty),
      qualification: optionalText(posting.qualification),
      preference: optionalText(posting.preference),
    }

    setIsSaving(true)
    setFormError(null)
    try {
      const saved = isEditMode
        ? await replaceCompanyCoverLetter(coverLetterId, {
            title: title.trim(),
            jobPosting: isManual ? postingPayload : null,
            items: itemPayload,
          })
        : isManual
          ? await createManualCompanyCoverLetter({
              title: optionalText(title),
              jobPosting: postingPayload,
              items: itemPayload,
            })
          : await createCompanyCoverLetter({
              jobPostingId: linkedJobPostingId,
              title: title.trim(),
              items: itemPayload,
            })
      navigate(`/cover-letter-result?coverLetterId=${saved.id}`, { replace: true })
    } catch (error) {
      setFormError(error.message ?? '자기소개서를 저장하지 못했습니다.')
    } finally {
      setIsSaving(false)
    }
  }

  if (isEditMode && !hasValidId) {
    return (
      <section className={styles.statePage}>
        <p>수정할 자기소개서를 찾을 수 없습니다.</p>
        <Link to="/cover-letter-list">목록으로 돌아가기</Link>
      </section>
    )
  }

  if (isLoading) {
    return <section className={styles.statePage}>자기소개서를 불러오는 중입니다.</section>
  }

  if (loadError) {
    return (
      <section className={styles.statePage} role="alert">
        <p>{loadError.message ?? '자기소개서를 불러오지 못했습니다.'}</p>
        <Link to="/cover-letter-list">목록으로 돌아가기</Link>
      </section>
    )
  }

  return (
    <div className={styles.page}>
      <main className={styles.content}>
        <header className={styles.pageHeader}>
          <div>
            <div className={styles.headingTitle}>
              <span className={styles.headingIcon} aria-hidden="true">01</span>
              <h1>채용공고 입력</h1>
            </div>
            <p className={styles.headingDescription}>첨삭 기준이 되는 채용공고입니다.</p>
          </div>
          <span>총 답변 {answerTotal.toLocaleString()}자</span>
        </header>

        {isEditMode && (
          <div className={styles.feedbackWarning} role="note">
            <span aria-hidden="true">!</span>
            <div>
              <strong>첨삭을 받은 자기소개서인가요?</strong>
              <p>문항·답변 또는 직접 입력 공고 내용을 수정해 저장하면 관련 기존 첨삭은 삭제되며, 다시 첨삭받아야 합니다.</p>
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <section className={`${styles.card} ${styles.postingCard}`} aria-labelledby="posting-heading">
            <h2 className={styles.visuallyHidden} id="posting-heading">채용공고 입력란</h2>
            {!isManual && <div className={styles.badgeRow}><span className={styles.readOnlyBadge}>연결 공고 · 읽기 전용</span></div>}

            <div className={styles.fieldGrid}>
              <label className={styles.fullField}>
                <span>공고 제목</span>
                <input
                  disabled={!isManual}
                  maxLength={255}
                  placeholder="공고 제목을 입력해 주세요."
                  required={isManual}
                  value={posting.postingTitle}
                  onChange={(event) => updatePosting('postingTitle', event.target.value)}
                />
              </label>

              <label>
                <span>기업명 <small>선택</small></span>
                <input disabled={!isManual} maxLength={255} placeholder="기업명" value={posting.companyName} onChange={(event) => updatePosting('companyName', event.target.value)} />
              </label>
              <label>
                <span>직무</span>
                <input disabled={!isManual} maxLength={255} placeholder="예: 백엔드 개발자" required={isManual} value={posting.jobRoleName} onChange={(event) => updatePosting('jobRoleName', event.target.value)} />
              </label>
              <label>
                <span>경력 구분 <small>선택</small></span>
                <input disabled={!isManual} maxLength={50} placeholder="예: 신입, 경력 3년" value={posting.careerType} onChange={(event) => updatePosting('careerType', event.target.value)} />
              </label>
              <label>
                <span>고용 형태 <small>선택</small></span>
                <input disabled={!isManual} maxLength={255} placeholder="예: 정규직" value={posting.hireType} onChange={(event) => updatePosting('hireType', event.target.value)} />
              </label>

              <label className={styles.fullField}>
                <span>직무 상세 <small>선택</small></span>
                <textarea disabled={!isManual} placeholder="포지션의 상세 설명을 입력해 주세요." rows={3} value={posting.positionDetail} onChange={(event) => updatePosting('positionDetail', event.target.value)} />
              </label>
              <label className={styles.fullField}>
                <span>주요 업무 <small>선택</small></span>
                <textarea disabled={!isManual} placeholder="주요 업무를 작성해 주세요." rows={4} value={posting.mainDuty} onChange={(event) => updatePosting('mainDuty', event.target.value)} />
              </label>
              <label>
                <span>자격 요건 <small>선택</small></span>
                <textarea disabled={!isManual} placeholder="자격 요건을 작성해 주세요." rows={4} value={posting.qualification} onChange={(event) => updatePosting('qualification', event.target.value)} />
              </label>
              <label>
                <span>우대 사항 <small>선택</small></span>
                <textarea disabled={!isManual} placeholder="우대 사항을 작성해 주세요." rows={4} value={posting.preference} onChange={(event) => updatePosting('preference', event.target.value)} />
              </label>
            </div>
          </section>

          <section className={styles.questionsSection} aria-labelledby="questions-heading">
            <div className={styles.questionsHeader}>
              <div>
                <div className={styles.headingTitle}>
                  <span className={styles.headingIcon} aria-hidden="true">02</span>
                  <h2 id="questions-heading">자기소개서 입력</h2>
                </div>
                <p className={styles.headingDescription}>자기소개서는 최소 1개 이상 작성해 주세요. 문항별 질문과 답변을 입력할 수 있습니다.</p>
              </div>
              <span>{items.length} / {MAX_ITEMS}</span>
            </div>

            <label className={styles.titleField}>
              <span>자기소개서 제목 <small>{isEditMode || !isManual ? '필수' : '선택'}</small></span>
              <input
                maxLength={255}
                placeholder="비워두면 회사명 또는 자기소개서 번호로 저장됩니다."
                required={isEditMode || !isManual}
                value={title}
                onChange={(event) => setTitle(event.target.value)}
              />
            </label>

            <div className={styles.questionList}>
              {items.map((item, index) => {
                const limit = item.characterLimit === '' ? null : Number(item.characterLimit)
                const isOverLimit = limit !== null && item.answer.length > limit
                return (
                  <article className={styles.questionCard} key={item.clientKey}>
                    <div className={styles.questionTopline}>
                      <span>Question {String(index + 1).padStart(2, '0')}</span>
                      <button type="button" disabled={items.length === 1} onClick={() => removeItem(item.clientKey)}>
                        문항 삭제
                      </button>
                    </div>

                    <label>
                      <span>문항 <b>*</b></span>
                      <input
                        maxLength={1000}
                        placeholder={`${index + 1}번 문항을 입력해 주세요.`}
                        required
                        value={item.questionText}
                        onChange={(event) => updateItem(item.clientKey, 'questionText', event.target.value)}
                      />
                    </label>

                    <label>
                      <span>답변 작성</span>
                      <textarea
                        className={isOverLimit ? styles.invalidInput : undefined}
                        placeholder="문항에 대한 답변을 작성해 주세요."
                        rows={7}
                        value={item.answer}
                        onChange={(event) => updateItem(item.clientKey, 'answer', event.target.value)}
                      />
                    </label>

                    <div className={styles.answerMeta}>
                      <label>
                        글자 수 제한
                        <input
                          aria-label={`${index + 1}번 문항 글자 수 제한`}
                          min="1"
                          type="number"
                          value={item.characterLimit}
                          onChange={(event) => updateItem(item.clientKey, 'characterLimit', event.target.value)}
                        />
                      </label>
                      <span className={isOverLimit ? styles.overLimit : undefined}>
                        {item.answer.length.toLocaleString()} / {limit?.toLocaleString() ?? '제한 없음'}자
                      </span>
                    </div>
                  </article>
                )
              })}
            </div>

            <button className={styles.addButton} type="button" disabled={items.length >= MAX_ITEMS} onClick={addItem}>
              <span aria-hidden="true">＋</span>
              {items.length >= MAX_ITEMS ? '문항은 최대 30개까지 추가할 수 있습니다.' : '문항 추가'}
            </button>
          </section>

          {formError && <p className={styles.formError} role="alert">{formError}</p>}

          <div className={styles.submitArea}>
            <p>입력한 내용은 저장 후 자기소개서 상세 화면에서 확인할 수 있습니다.</p>
            <button type="submit" disabled={isSaving}>
              {isSaving ? '저장하는 중...' : isEditMode ? '수정 내용 저장' : '자기소개서 저장'}
            </button>
          </div>
        </form>
      </main>
    </div>
  )
}

export default CoverLetterWritePage

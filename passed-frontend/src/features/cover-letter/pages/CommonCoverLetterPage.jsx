import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import {
  createCommonCoverLetter,
  getCommonCoverLetter,
  getCommonCoverLetterQuestions,
  updateCommonCoverLetter,
} from '../api/index.js'
import styles from './styles/CommonCoverLetterPage.module.css'

const MIN_LENGTH = 500
const MAX_LENGTH = 1000

const CommonCoverLetterPage = ({ onboarding = false }) => {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const initialAnswersRef = useRef('')
  const [questions, setQuestions] = useState([])
  const [answers, setAnswers] = useState({})
  const [existing, setExisting] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    Promise.allSettled([
      getCommonCoverLetterQuestions({ signal: controller.signal }),
      getCommonCoverLetter({ signal: controller.signal }),
    ]).then(([questionResult, coverLetterResult]) => {
      if (controller.signal.aborted) return
      if (questionResult.status === 'rejected') throw questionResult.reason
      const activeQuestions = questionResult.value ?? []
      setQuestions(activeQuestions)
      if (coverLetterResult.status === 'fulfilled') {
        const nextAnswers = Object.fromEntries((coverLetterResult.value.items ?? []).map((item) => [item.questionId, item.answer ?? '']))
        setExisting(true)
        setAnswers(nextAnswers)
        initialAnswersRef.current = JSON.stringify({
          items: activeQuestions.map((question) => ({
            questionId: question.questionId,
            answer: (nextAnswers[question.questionId] ?? '').trim(),
          })),
        })
      } else if (coverLetterResult.reason?.status !== 404) {
        throw coverLetterResult.reason
      }
    }).catch((requestError) => {
      if (requestError.name !== 'AbortError') setError(requestError.message)
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [])

  const invalidQuestionIds = useMemo(
    () => questions.filter((question) => (answers[question.questionId]?.trim().length ?? 0) < MIN_LENGTH).map((question) => question.questionId),
    [answers, questions],
  )

  const submit = async (event) => {
    event.preventDefault()
    setSubmitted(true)
    if (invalidQuestionIds.length) {
      document.getElementById(`cover-letter-${invalidQuestionIds[0]}`)?.focus()
      return
    }
    setSaving(true); setError('')
    const body = { items: questions.map((question) => ({ questionId: question.questionId, answer: answers[question.questionId].trim() })) }
    const documentChanged = JSON.stringify(body) !== initialAnswersRef.current
    try {
      if (existing) await updateCommonCoverLetter(body)
      else await createCommonCoverLetter(body)
      if (onboarding) navigate('/onboarding/analysis')
      else if (searchParams.get('returnTo') === 'mypage') {
        navigate('/mypage', {
          state: documentChanged ? { documentsUpdated: true, updatedDocument: 'coverLetter' } : undefined,
        })
      } else navigate('/')
    } catch (requestError) { setError(requestError.message) } finally { setSaving(false) }
  }

  if (loading) return <main className={styles.page}><PageLoading title="자기소개서 문항을 불러오고 있어요" /></main>

  return (
    <main className={styles.page}>
      <header className={styles.heading}>
        <div><h1>자기소개서 {existing ? '수정' : '작성'}</h1><p>필수 항목에 솔직하고 구체적으로 작성해주세요.</p></div>
        <span><b>*</b> 모든 문항은 {MIN_LENGTH}자 이상 작성해야 합니다.</span>
      </header>
      <form className={styles.form} onSubmit={submit} noValidate>
        {questions.map((question, index) => {
          const value = answers[question.questionId] ?? ''
          const invalid = submitted && value.trim().length < MIN_LENGTH
          return (
            <section className={`${styles.question} ${invalid ? styles.invalid : ''}`} key={question.questionId}>
              <div className={styles.questionHeading}>
                <span>{index + 1}</span>
                <div><h2>{question.questionText} <b>*</b></h2>{question.guideText && <p>{question.guideText}</p>}</div>
                <small className={value.length >= MIN_LENGTH ? styles.validCount : ''}>{value.length.toLocaleString()} / {MAX_LENGTH.toLocaleString()}자</small>
              </div>
              <textarea id={`cover-letter-${question.questionId}`} maxLength={MAX_LENGTH} rows="7" value={value} placeholder={`${question.questionText} 내용을 입력해주세요.`} aria-invalid={invalid} aria-describedby={invalid ? `cover-letter-error-${question.questionId}` : undefined} onChange={(event) => { setAnswers((current) => ({ ...current, [question.questionId]: event.target.value })); setError('') }} />
              {invalid && <p className={styles.fieldError} id={`cover-letter-error-${question.questionId}`} role="alert">분석을 진행하려면 {MIN_LENGTH}자 이상 작성해주세요. 현재 {value.trim().length}자입니다.</p>}
            </section>
          )
        })}
        {!questions.length && <section className={styles.empty}>작성 가능한 자기소개서 문항이 없습니다.</section>}
        {error && <p className={styles.error} role="alert">{error}</p>}
        <button className={styles.submit} type="submit" disabled={saving || !questions.length}>{saving ? '저장 중…' : existing ? '수정 완료' : '작성 완료'} <span aria-hidden="true">→</span></button>
      </form>
    </main>
  )
}

export default CommonCoverLetterPage

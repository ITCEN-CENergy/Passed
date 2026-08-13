import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  generateCoverLetterItemFeedback,
  generateCoverLetterOverallFeedback,
  getCoverLetterItemFeedback,
  getCoverLetterOverallFeedback,
} from '../api'
import { useCompanyCoverLetter } from '../hooks'
import styles from './styles/CoverLetterResultPage.module.css'

function formatDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function initialFeedbackState(items) {
  return Object.fromEntries(items.map((item) => [item.id, {
    feedback: null,
    isLoading: true,
    isGenerating: false,
    error: null,
  }]))
}

function scoreTone(score) {
  if (score === 'SUFFICIENT') return styles.scoreGood
  if (score === 'INSUFFICIENT') return styles.scoreCaution
  return styles.scoreNeedsWork
}

const CoverLetterResultPage = () => {
  const [searchParams] = useSearchParams()
  const coverLetterId = Number(searchParams.get('coverLetterId'))
  const isValidId = Number.isSafeInteger(coverLetterId) && coverLetterId > 0
  const { coverLetter, error, isLoading, reload } = useCompanyCoverLetter(
    isValidId ? coverLetterId : null,
  )
  const [feedbackByItem, setFeedbackByItem] = useState({})
  const [overallFeedback, setOverallFeedback] = useState(null)
  const [bulkFeedback, setBulkFeedback] = useState({
    isRunning: false,
    completed: 0,
    total: 0,
    failed: 0,
  })
  const bulkRunRef = useRef(false)

  useEffect(() => {
    if (!coverLetter?.items?.length) {
      setFeedbackByItem({})
      setOverallFeedback(null)
      setBulkFeedback({ isRunning: false, completed: 0, total: 0, failed: 0 })
      return undefined
    }

    const controller = new AbortController()
    setFeedbackByItem(initialFeedbackState(coverLetter.items))
    setBulkFeedback({ isRunning: false, completed: 0, total: 0, failed: 0 })
    bulkRunRef.current = false

    getCoverLetterOverallFeedback(coverLetter.id, { signal: controller.signal })
      .then(setOverallFeedback)
      .catch((overallError) => {
        if (overallError?.name === 'AbortError') return
        const hasNoFeedback = overallError?.status === 404
          || overallError?.code === 'COVER_LETTER_FEEDBACK_NOT_FOUND'
        if (!hasNoFeedback) {
          setBulkFeedback({ isRunning: false, completed: 0, total: 0, failed: 1 })
        }
      })

    coverLetter.items.forEach((item) => {
      getCoverLetterItemFeedback(item.id, { signal: controller.signal })
        .then((feedback) => {
          setFeedbackByItem((current) => ({
            ...current,
            [item.id]: { feedback, isLoading: false, isGenerating: false, error: null },
          }))
        })
        .catch((feedbackError) => {
          if (feedbackError?.name === 'AbortError') return
          const hasNoFeedback = feedbackError?.status === 404
            || feedbackError?.code === 'COVER_LETTER_ITEM_FEEDBACK_NOT_FOUND'
          setFeedbackByItem((current) => ({
            ...current,
            [item.id]: {
              feedback: null,
              isLoading: false,
              isGenerating: false,
              error: hasNoFeedback ? null : feedbackError,
            },
          }))
        })
    })

    return () => controller.abort()
  }, [coverLetter])

  const generateFeedback = async (itemId) => {
    setFeedbackByItem((current) => ({
      ...current,
      [itemId]: {
        ...current[itemId],
        isGenerating: true,
        error: null,
      },
    }))

    try {
      const feedback = await generateCoverLetterItemFeedback(itemId)
      setFeedbackByItem((current) => ({
        ...current,
        [itemId]: { feedback, isLoading: false, isGenerating: false, error: null },
      }))
      return true
    } catch (feedbackError) {
      setFeedbackByItem((current) => ({
        ...current,
        [itemId]: {
          ...current[itemId],
          isGenerating: false,
          error: feedbackError,
        },
      }))
      return false
    }
  }

  const generateAllFeedback = async () => {
    if (bulkRunRef.current) return

    const targets = coverLetter?.items?.filter((item) => item.answer?.trim()) ?? []
    if (!targets.length) return

    bulkRunRef.current = true
    setBulkFeedback({ isRunning: true, completed: 0, total: targets.length, failed: 0 })

    try {
      const result = await generateCoverLetterOverallFeedback(coverLetter.id)
      setOverallFeedback(result)
      setFeedbackByItem((current) => {
        const next = { ...current }
        result.items.forEach((feedback) => {
          next[feedback.companyCoverLetterItemId] = {
            feedback,
            isLoading: false,
            isGenerating: false,
            error: null,
          }
        })
        return next
      })
      setBulkFeedback({
        isRunning: false,
        completed: targets.length,
        total: targets.length,
        failed: 0,
      })
    } catch (bulkError) {
      setBulkFeedback({
        isRunning: false,
        completed: 0,
        total: targets.length,
        failed: targets.length,
      })
    } finally {
      bulkRunRef.current = false
    }
  }

  if (!isValidId) {
    return (
      <section className={styles.statePage}>
        <h1>확인할 자기소개서를 찾을 수 없습니다.</h1>
        <Link className={styles.primaryButton} to="/cover-letter-list">목록으로 돌아가기</Link>
      </section>
    )
  }

  if (isLoading) {
    return <section className={styles.statePage}>자기소개서를 불러오는 중입니다.</section>
  }

  if (error || !coverLetter) {
    return (
      <section className={styles.statePage} role="alert">
        <p>{error?.message ?? '자기소개서를 불러오지 못했습니다.'}</p>
        <div className={styles.stateActions}>
          <button className={styles.secondaryButton} type="button" onClick={() => reload()}>
            다시 시도
          </button>
          <Link className={styles.primaryButton} to="/cover-letter-list">목록으로 돌아가기</Link>
        </div>
      </section>
    )
  }

  const answeredItems = coverLetter.items.filter((item) => item.answer?.trim())
  const feedbackStates = Object.values(feedbackByItem)
  const isFeedbackBusy = feedbackStates.some((state) => state.isLoading || state.isGenerating)
  const allAnsweredItemsHaveFeedback = answeredItems.length > 0
    && answeredItems.every((item) => feedbackByItem[item.id]?.feedback)
  const bulkSucceeded = bulkFeedback.completed - bulkFeedback.failed

  return (
    <div className={styles.page}>
      <main className={styles.content}>
        <div className={styles.topNavigation}>
          <Link className={styles.backLink} to="/cover-letter-list">← 자기소개서 목록</Link>
          <div className={styles.topActions}>
            <button
              className={styles.bulkFeedbackButton}
              disabled={!answeredItems.length || isFeedbackBusy || bulkFeedback.isRunning}
              type="button"
              onClick={generateAllFeedback}
            >
              {bulkFeedback.isRunning
                ? '전체 첨삭 중...'
                : allAnsweredItemsHaveFeedback
                  ? '전체 다시 첨삭'
                  : '전체 첨삭받기'}
            </button>
            <Link className={styles.editLink} to={`/cover-letter-write/${coverLetter.id}`}>자기소개서 수정</Link>
          </div>
        </div>

        <header className={styles.intro}>
          <div>
            <p className={styles.company}>{coverLetter.companyName || '기업명 미입력'}</p>
            <h1>{coverLetter.title}</h1>
            <p className={styles.posting}>{coverLetter.jobPostingTitle}</p>
          </div>
          <div className={styles.documentMeta}>
            <span>{coverLetter.items.length}개 문항</span>
            <span>최종 수정 {formatDate(coverLetter.updatedAt)}</span>
          </div>
        </header>

        <section className={styles.guide}>
          <span aria-hidden="true">AI</span>
          <div>
            <strong>문항별로 자기소개서 첨삭을 받을 수 있습니다.</strong>
            <p>답변을 수정해 저장하면 해당 문항의 기존 첨삭은 삭제되며, 다시 첨삭받아야 합니다.</p>
          </div>
        </section>

        {overallFeedback && (
          <section className={styles.overallResult} aria-label="자기소개서 종합 첨삭 결과">
            <div className={styles.overallHeading}>
              <div>
                <p>Overall feedback</p>
                <h2>종합 진단</h2>
              </div>
              <span className={`${styles.scoreBadge} ${scoreTone(overallFeedback.score)}`}>
                {overallFeedback.scoreLabel ?? overallFeedback.score}
              </span>
            </div>
            <p className={styles.overallSummary}>{overallFeedback.summary}</p>
            <div className={styles.overallColumns}>
              <div className={styles.overallStrengths}>
                <h3><span aria-hidden="true">✓</span> 잘된 점</h3>
                <p>{overallFeedback.strengths}</p>
              </div>
              <div className={styles.overallImprovements}>
                <h3><span aria-hidden="true">!</span> 우선 개선할 점</h3>
                <p>{overallFeedback.improvements}</p>
              </div>
            </div>
            <p className={styles.feedbackDate}>최근 종합 첨삭 {formatDate(overallFeedback.updatedAt)}</p>
          </section>
        )}

        {bulkFeedback.total > 0 && !bulkFeedback.isRunning && (
          <p
            className={bulkFeedback.failed ? styles.bulkPartialResult : styles.bulkSuccessResult}
            role="status"
          >
            {bulkFeedback.failed
              ? `전체 첨삭 ${bulkSucceeded}개 완료, ${bulkFeedback.failed}개 실패했습니다. 실패한 문항을 다시 시도해 주세요.`
              : `답변이 작성된 ${bulkFeedback.total}개 문항의 첨삭을 완료했습니다.`}
          </p>
        )}

        <section className={styles.items} aria-label="자기소개서 문항 및 첨삭 결과">
          {coverLetter.items.map((item) => {
            const state = feedbackByItem[item.id] ?? {
              feedback: null,
              isLoading: true,
              isGenerating: false,
              error: null,
            }
            const hasAnswer = Boolean(item.answer?.trim())
            return (
              <article className={styles.itemCard} key={item.id}>
                <div className={styles.itemHeading}>
                  <div>
                    <span>Question {String(item.displayOrder).padStart(2, '0')}</span>
                    <small>{item.characterLimit ? `${item.characterLimit.toLocaleString()}자 이내` : '글자 수 제한 없음'}</small>
                  </div>
                  <button
                    className={state.feedback ? styles.regenerateButton : styles.feedbackButton}
                    disabled={!hasAnswer || state.isGenerating || state.isLoading || bulkFeedback.isRunning}
                    type="button"
                    onClick={() => generateFeedback(item.id)}
                  >
                    {state.isGenerating
                      ? '첨삭 중...'
                      : state.feedback
                        ? '다시 첨삭받기'
                        : '첨삭받기'}
                  </button>
                </div>

                <section className={styles.originalSection}>
                  <h2>{item.questionText}</h2>
                  <p className={hasAnswer ? undefined : styles.emptyAnswer}>
                    {item.answer || '작성된 답변이 없습니다.'}
                  </p>
                  <span>{(item.answer?.length ?? 0).toLocaleString()}자</span>
                </section>

                {!hasAnswer && (
                  <p className={styles.feedbackNotice}>답변을 작성한 뒤 첨삭받을 수 있습니다.</p>
                )}
                {state.isLoading && (
                  <p className={styles.feedbackNotice} aria-live="polite">기존 첨삭을 확인하는 중입니다.</p>
                )}
                {state.error && (
                  <p className={styles.feedbackError} role="alert">
                    {state.error.message ?? '첨삭 요청을 처리하지 못했습니다.'}
                  </p>
                )}

                {state.feedback && (
                  <section className={styles.feedbackResult} aria-label={`${item.displayOrder}번 문항 첨삭 결과`}>
                    <div className={styles.feedbackTitle}>
                      <div>
                        <p>AI 첨삭 결과</p>
                        <h3>답변을 더 설득력 있게 다듬어 보세요.</h3>
                      </div>
                      <span className={`${styles.scoreBadge} ${scoreTone(state.feedback.score)}`}>
                        {state.feedback.scoreLabel ?? state.feedback.score}
                      </span>
                    </div>

                    {state.feedback.strengths && (
                      <div className={`${styles.feedbackBlock} ${styles.strengthBlock}`}>
                        <h4><span aria-hidden="true">✓</span> 잘된 점</h4>
                        <p>{state.feedback.strengths}</p>
                      </div>
                    )}

                    <div className={`${styles.feedbackBlock} ${styles.improvementBlock}`}>
                      <h4><span aria-hidden="true">!</span> 개선할 점</h4>
                      <p>{state.feedback.improvements}</p>
                    </div>

                    <div className={styles.suggestedAnswer}>
                      <div className={styles.suggestedHeading}>
                        <h4>추천 수정안</h4>
                        <span className={state.feedback.withinCharacterLimit ? styles.limitGood : styles.limitWarning}>
                          {state.feedback.suggestedAnswerLength.toLocaleString()}
                          {state.feedback.characterLimit
                            ? ` / ${state.feedback.characterLimit.toLocaleString()}자`
                            : '자'}
                        </span>
                      </div>
                      <p>{state.feedback.suggestedAnswer}</p>
                    </div>

                    <p className={styles.feedbackDate}>최근 첨삭 {formatDate(state.feedback.updatedAt)}</p>
                  </section>
                )}
              </article>
            )
          })}
        </section>
      </main>
    </div>
  )
}

export default CoverLetterResultPage

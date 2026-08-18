import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import {
  generateCoverLetterItemFeedback,
  generateCoverLetterOverallFeedback,
  generateCoverLetterSuggestedAnswer,
  getCoverLetterItemFeedback,
  getCoverLetterOverallFeedback,
} from '../api'
import { FeedbackContent } from '../components'
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
    isSuggesting: false,
    error: null,
  }]))
}

function scoreTone(score) {
  if (score === 'SUFFICIENT') return styles.scoreGood
  if (score === 'INSUFFICIENT') return styles.scoreCaution
  return styles.scoreNeedsWork
}

const CompanyCoverLetterResult = () => {
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
            [item.id]: { feedback, isLoading: false, isGenerating: false, isSuggesting: false, error: null },
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
              isSuggesting: false,
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
        [itemId]: { feedback, isLoading: false, isGenerating: false, isSuggesting: false, error: null },
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

  const generateSuggestedAnswer = async (itemId) => {
    setFeedbackByItem((current) => ({
      ...current,
      [itemId]: { ...current[itemId], isSuggesting: true, error: null },
    }))
    try {
      const feedback = await generateCoverLetterSuggestedAnswer(itemId)
      setFeedbackByItem((current) => ({
        ...current,
        [itemId]: { ...current[itemId], feedback, isSuggesting: false, error: null },
      }))
    } catch (suggestionError) {
      setFeedbackByItem((current) => ({
        ...current,
        [itemId]: { ...current[itemId], isSuggesting: false, error: suggestionError },
      }))
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
            isSuggesting: false,
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
    } catch {
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
    return (
      <div className={styles.page}>
        <main className={`${styles.content} ${styles.loadingContent}`}>
          <PageLoading
            title="자기소개서를 불러오고 있어요"
            description="작성한 문항과 첨삭 결과를 확인하고 있어요."
          />
        </main>
      </div>
    )
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
  const generatingState = feedbackStates.find((state) => state.isGenerating || state.isSuggesting)
  const allAnsweredItemsHaveFeedback = answeredItems.length > 0
    && answeredItems.every((item) => feedbackByItem[item.id]?.feedback)
  const bulkSucceeded = bulkFeedback.completed - bulkFeedback.failed
  const loadingCopy = generatingState?.isSuggesting
    ? {
        title: '추천 수정안을 생성하고 있어요',
        description: '첨삭 결과와 글자 수 제한을 반영해 답변을 다듬고 있어요.',
      }
    : bulkFeedback.isRunning
      ? {
          title: '자기소개서 전체를 첨삭하고 있어요',
          description: '채용공고와 모든 답변을 비교해 종합 피드백을 만들고 있어요.',
        }
      : {
          title: '자기소개서 답변을 첨삭하고 있어요',
          description: '채용공고와 선택한 답변을 분석해 개선점을 찾고 있어요.',
        }

  if (bulkFeedback.isRunning || generatingState) {
    return (
      <div className={styles.page}>
        <main className={`${styles.content} ${styles.loadingContent}`}>
          <PageLoading {...loadingCopy} />
        </main>
      </div>
    )
  }

  return (
    <div className={styles.page}>
      <main className={styles.content}>
        <div className={styles.topNavigation}>
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
              <h2>종합 진단</h2>
              <span className={`${styles.scoreBadge} ${scoreTone(overallFeedback.score)}`}>
                {overallFeedback.scoreLabel ?? overallFeedback.score}
              </span>
            </div>
            <div className={styles.overallSummary}><FeedbackContent text={overallFeedback.summary} /></div>
            <div className={styles.overallColumns}>
              <div className={styles.overallStrengths}>
                <h3><span aria-hidden="true">✓</span> 잘된 점</h3>
                <FeedbackContent text={overallFeedback.strengths} />
              </div>
              <div className={styles.overallImprovements}>
                <h3><span aria-hidden="true">!</span> 우선 개선할 점</h3>
                <FeedbackContent text={overallFeedback.improvements} />
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
              isSuggesting: false,
              error: null,
            }
            const hasAnswer = Boolean(item.answer?.trim())
            return (
              <article className={styles.itemCard} key={item.id}>
                <div className={styles.itemHeading}>
                  <div>
                    <span>문항 {item.displayOrder}</span>
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

                    <div className={styles.improvementArea}>
                      <h4 className={styles.improvementTitle}><span aria-hidden="true">!</span> 미흡한 부분</h4>
                      <div className={styles.improvementGrid}>
                        <article className={styles.improvementBlock}>
                          <FeedbackContent text={state.feedback.shortcomings} />
                        </article>
                      </div>
                    </div>

                    <div className={styles.improvementArea}>
                      <h4 className={styles.improvementTitle}>추천 수정 방향</h4>
                      <div className={styles.improvementGrid}>
                        <article className={styles.improvementBlock}>
                          <FeedbackContent text={state.feedback.recommendedRevisionDirection} />
                        </article>
                      </div>
                    </div>

                    {state.feedback.suggestedAnswer ? (
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
                        <FeedbackContent text={state.feedback.suggestedAnswer} />
                      </div>
                    ) : (
                      <div className={styles.suggestionPrompt}>
                        <div>
                          <h4>추천 수정안</h4>
                          <p>개선점을 반영한 답변이 필요할 때 생성할 수 있습니다.</p>
                        </div>
                        <button type="button" disabled={state.isSuggesting} onClick={() => generateSuggestedAnswer(item.id)}>
                          {state.isSuggesting ? '생성 중...' : '추천 수정안 생성하기'}
                        </button>
                      </div>
                    )}

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

export default CompanyCoverLetterResult

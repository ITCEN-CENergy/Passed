import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCompanyCoverLetters } from '../hooks/index.js'
import styles from './CoverLetterListPage.module.css'

function formatDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

const CoverLetterListPage = () => {
  const navigate = useNavigate()
  const { coverLetters, error, isLoading, reload, remove } = useCompanyCoverLetters()
  const [deletingId, setDeletingId] = useState(null)
  const [actionError, setActionError] = useState(null)

  const removeCoverLetter = async (coverLetter) => {
    if (!window.confirm(`'${coverLetter.jobPostingTitle}' 자기소개서를 삭제할까요?`)) return

    setActionError(null)
    setDeletingId(coverLetter.id)

    try {
      await remove(coverLetter.id)
    } catch (deleteError) {
      setActionError(deleteError.message)
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div className={styles.page}>
      <section className={styles.content} aria-labelledby="cover-letter-title">
        <div className={styles.intro}>
          <h1 id="cover-letter-title">자기소개서 목록</h1>
          <p>작성한 자기소개서를 확인하고 관리하세요.</p>
        </div>

        {isLoading && <p className={styles.stateMessage}>자기소개서를 불러오는 중입니다.</p>}

        {!isLoading && error && (
          <div className={styles.stateMessage} role="alert">
            <p>{error.message}</p>
            <button className={styles.retryButton} type="button" onClick={() => reload()}>
              다시 시도
            </button>
          </div>
        )}

        {actionError && <p className={styles.actionError} role="alert">{actionError}</p>}

        {!isLoading && !error && coverLetters.length === 0 && (
          <p className={styles.empty}>작성한 자기소개서가 없습니다.</p>
        )}

        {!isLoading && !error && coverLetters.length > 0 && (
          <ul className={styles.list} aria-label="자기소개서 목록">
            {coverLetters.map((coverLetter) => (
              <li className={styles.card} key={coverLetter.id}>
                <div className={styles.cardInfo}>
                  <h2>{coverLetter.jobPostingTitle}</h2>
                  <p>작성일: {formatDate(coverLetter.createdAt)}</p>
                </div>
                <div className={styles.cardActions}>
                  <button
                    className={styles.checkButton}
                    type="button"
                    onClick={() => navigate(`/cover-letter-result?coverLetterId=${coverLetter.id}`)}
                  >
                    확인하기
                  </button>
                  <button
                    className={styles.deleteButton}
                    disabled={deletingId === coverLetter.id}
                    type="button"
                    onClick={() => removeCoverLetter(coverLetter)}
                  >
                    {deletingId === coverLetter.id ? '삭제 중' : '삭제하기'}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

export default CoverLetterListPage

import { Link } from 'react-router-dom'
import { useCompanyCoverLetters } from '../hooks'
import styles from './styles/CompanyCoverLetterList.module.css'

function formatDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
    .format(date)
    .replace(/\. /g, '. ')
}

const CompanyCoverLetterList = () => {
  const { coverLetters, error, isLoading, reload } = useCompanyCoverLetters()

  return (
    <div className={styles.page}>
      <main className={styles.content} aria-labelledby="cover-letter-title">
        <Link className={styles.backLink} to="/">
          <span aria-hidden="true">←</span>
          학습 로드맵
        </Link>

        <div className={styles.pageHeader}>
          <h1 id="cover-letter-title">자기소개서 목록</h1>
          <Link className={styles.writeButton} to="/cover-letter-write">
            직접 자기소개서 입력하기
          </Link>
        </div>

        {isLoading && (
          <div className={styles.stateCard} aria-live="polite">
            자기소개서를 불러오는 중입니다.
          </div>
        )}

        {!isLoading && error && (
          <div className={styles.stateCard} role="alert">
            <p>{error.message}</p>
            <button className={styles.retryButton} type="button" onClick={() => reload()}>
              다시 시도
            </button>
          </div>
        )}

        {!isLoading && !error && coverLetters.length === 0 && (
          <div className={styles.emptyState}>
            <strong>작성한 자기소개서가 없습니다.</strong>
            <p>지원할 채용공고의 자기소개서를 직접 입력해 보세요.</p>
          </div>
        )}

        {!isLoading && !error && coverLetters.length > 0 && (
          <ul className={styles.list} aria-label="자기소개서 목록">
            {coverLetters.map((coverLetter) => (
              <li key={coverLetter.id}>
                <Link
                  className={styles.card}
                  to={`/cover-letter-result?coverLetterId=${coverLetter.id}`}
                  aria-label={`${coverLetter.title} 자기소개서 확인`}
                >
                  <h2>{coverLetter.title}</h2>
                  <p>최종 수정일: {formatDate(coverLetter.updatedAt)}</p>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  )
}

export default CompanyCoverLetterList

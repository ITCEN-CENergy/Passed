import { Link, useSearchParams } from 'react-router-dom'
import { useCompanyCoverLetter } from '../../features/cover-letter/index.js'
import styles from './CoverLetterResultPage.module.css'

function formatDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

const CoverLetterResultPage = () => {
  const [searchParams] = useSearchParams()
  const coverLetterId = Number(searchParams.get('coverLetterId'))
  const isValidId = Number.isSafeInteger(coverLetterId) && coverLetterId > 0
  const { coverLetter, error, isLoading, reload } = useCompanyCoverLetter(
    isValidId ? coverLetterId : null,
  )

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

  return (
    <div className={styles.page}>
      <main className={styles.content}>
        <Link className={styles.backLink} to="/cover-letter-list">← 목록으로</Link>

        <header className={styles.intro}>
          <p className={styles.company}>{coverLetter.companyName}</p>
          <h1>{coverLetter.jobPostingTitle}</h1>
          <p className={styles.title}>{coverLetter.title}</p>
          <p className={styles.meta}>작성일: {formatDate(coverLetter.createdAt)}</p>
        </header>

        <section className={styles.items} aria-label="자기소개서 문항">
          {coverLetter.items.map((item) => (
            <article className={styles.itemCard} key={item.id}>
              <div className={styles.itemHeading}>
                <span>문항 {item.displayOrder}</span>
                <small>{item.characterLimit ? `${item.characterLimit.toLocaleString()}자 이내` : '글자 수 제한 없음'}</small>
              </div>
              <h2>{item.questionText}</h2>
              <p>{item.answer || '작성된 답변이 없습니다.'}</p>
            </article>
          ))}
        </section>
      </main>
    </div>
  )
}

export default CoverLetterResultPage

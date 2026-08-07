import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Header from '../../../shared/components/Header.jsx'
import styles from './styles/CompanyCoverLetterList.module.css'

const initialCoverLetters = [
  { id: 1, title: '2024 하반기 삼성전자 공채 자기소개서', date: '2024.07.15' },
  { id: 2, title: '네이버 AI 리서치 연구원 수시 채용', date: '2024.07.28' },
  { id: 3, title: '현대자동차 연구개발직 수시 채용', date: '2024.07.10' },
]

const CompanyCoverLetterList = () => {
  const [coverLetters, setCoverLetters] = useState(initialCoverLetters)
  const navigate = useNavigate()

  const removeCoverLetter = (id) => {
    const coverLetter = coverLetters.find((item) => item.id === id)

    if (coverLetter && window.confirm(`'${coverLetter.title}' 자기소개서를 삭제할까요?`)) {
      setCoverLetters((items) => items.filter((item) => item.id !== id))
    }
  }

  return (
    <div className={styles.page}>
      <Header />

      <main className={styles.content}>
        <section className={styles.intro} aria-labelledby="cover-letter-title">
          <h1 id="cover-letter-title">자기소개서 목록</h1>
          <p>작성한 자기소개서를 확인하고 관리하세요.</p>
        </section>

        <section className={styles.listSection} aria-label="자기소개서 목록">
          {coverLetters.length > 0 ? (
            <ul className={styles.list}>
              {coverLetters.map((coverLetter) => (
                <li className={styles.card} key={coverLetter.id}>
                  <div className={styles.cardInfo}>
                    <h2>{coverLetter.title}</h2>
                    <p>작성일: {coverLetter.date}</p>
                  </div>
                  <div className={styles.cardActions}>
                    <button
                      className={styles.checkButton}
                      type="button"
                      onClick={() => navigate('/cover-letter-result')}
                    >
                      확인하기
                    </button>
                    <button
                      className={styles.deleteButton}
                      type="button"
                      onClick={() => removeCoverLetter(coverLetter.id)}
                    >
                      삭제하기
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <p className={styles.empty}>작성한 자기소개서가 없습니다.</p>
          )}
        </section>
      </main>

      <footer className={styles.footer}>© Passed Inc. All rights reserved.</footer>
    </div>
  )
}

export default CompanyCoverLetterList

import styles from './RecommendationJourney.module.css'

const STEPS = [
  {
    title: '채용공고 매칭',
    description: '희망 조건과 내 스킬로 공고를 찾아요',
  },
  {
    title: '적합도 분석',
    description: '강점과 보완할 역량을 확인해요',
  },
  {
    title: '맞춤형 취업 코칭',
    description: '학습 로드맵과 자기소개서 첨삭을 시작해요',
  },
]

const PHASE_INDEX = {
  matching: 0,
  analysis: 1,
  results: 1,
  report: 2,
}

const RecommendationJourney = ({ phase = 'matching', compact = false, action = null }) => {
  const currentIndex = PHASE_INDEX[phase] ?? 0

  return (
    <section className={`${styles.journey} ${compact ? styles.compact : ''}`} aria-label="채용공고 매칭부터 맞춤형 취업 코칭까지의 과정">
      <div className={styles.intro}>
        <div className={styles.introCopy}>
          <h2>PASSED 합격 코칭 과정</h2>
          <p>매칭 리포트를 확인하면 학습 로드맵과 공고 맞춤 자기소개서 첨삭이 열려요.</p>
        </div>
        {action && <div className={styles.journeyAction}>{action}</div>}
      </div>
      <ol>
        {STEPS.map((step, index) => {
          const completed = index < currentIndex
          const current = index === currentIndex
          return (
            <li className={completed ? styles.completed : current ? styles.current : styles.upcoming} key={step.title} aria-current={current ? 'step' : undefined}>
              <span className={styles.marker} aria-hidden="true">{index + 1}</span>
              <div>
                <strong>{step.title}</strong>
                <small>{step.description}</small>
              </div>
              {index < STEPS.length - 1 && <span className={styles.connector} aria-hidden="true" />}
            </li>
          )
        })}
      </ol>
    </section>
  )
}

export default RecommendationJourney

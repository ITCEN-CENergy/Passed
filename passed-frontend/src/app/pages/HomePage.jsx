import { Link } from 'react-router-dom'
import heroBanner from '../../assets/images/home-ai-coaching-banner.webp'
import { PageLoading } from '../../common/components/index.js'
import useAuthStore from '../../features/auth/model/useAuthStore.js'
import { RecommendationPage } from '../../features/recommendation/pages/index.js'
import styles from './HomePage.module.css'

const matchingSteps = [
  {
    number: '01',
    title: '이력과 역량 분석',
    description: '등록한 이력서와 보유 스킬을 바탕으로 현재 역량을 정리해요.',
  },
  {
    number: '02',
    title: '맞춤 공고 매칭',
    description: '희망 산업과 직무에서 내 역량에 잘 맞는 공고를 선별해요.',
  },
  {
    number: '03',
    title: '합격 준비 연결',
    description: '부족한 역량의 학습 로드맵과 공고별 자기소개서 첨삭으로 이어가요.',
  },
]

const GuestRecommendation = () => (
  <div className={styles.guestPanel}>
    <div className={styles.guestCopy}>
      <span className={styles.sectionEyebrow}>AI JOB MATCHING</span>
      <h2>로그인하면 내 역량에 맞는 채용공고를 바로 만날 수 있어요</h2>
      <p>
        단순히 공고를 나열하지 않아요. 내 스킬과 희망 직무를 함께 분석해
        지원 우선순위와 보완할 역량까지 알려드려요.
      </p>
      <div className={styles.guestActions}>
        <Link className={styles.primaryLink} to="/login">로그인하고 맞춤 추천받기</Link>
        <Link className={styles.secondaryLink} to="/job-postings">채용공고 먼저 검색하기</Link>
      </div>
    </div>

    <ol className={styles.matchingSteps} aria-label="맞춤 채용공고 추천 과정">
      {matchingSteps.map((step) => (
        <li key={step.number}>
          <span>{step.number}</span>
          <div>
            <h3>{step.title}</h3>
            <p>{step.description}</p>
          </div>
        </li>
      ))}
    </ol>
  </div>
)

function HomePage() {
  const user = useAuthStore((state) => state.user)
  const isChecking = useAuthStore((state) => state.isChecking)

  return (
    <div className={styles.home}>
      <section className={styles.hero} aria-label="PASSED AI 취업 코칭 소개">
        <img
          src={heroBanner}
          alt="AI 취업 코칭, 나에게 맞는 채용공고부터 합격을 위한 취업 코칭. 맞춤 채용공고 추천, 학습 로드맵, 자기소개서 첨삭"
        />
      </section>

      <section className={styles.recommendationSection} aria-label="맞춤 채용공고 추천">
        {isChecking ? (
          <PageLoading
            title="맞춤 채용공고를 준비하고 있어요"
            description="로그인 상태와 최근 추천 결과를 확인하고 있어요."
          />
        ) : user ? (
          <>
            <header className={styles.sectionHeader}>
              <div>
                <h2 id="home-recommendation-title">채용공고 추천</h2>
                <p>저장된 희망 조건으로 추천을 이어보거나, 새로운 조건으로 다시 추천받아 보세요.</p>
              </div>
              <Link to="/job-postings">전체 채용공고 보기</Link>
            </header>
            <RecommendationPage embedded />
          </>
        ) : (
          <GuestRecommendation />
        )}
      </section>
    </div>
  )
}

export default HomePage

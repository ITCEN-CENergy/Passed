import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { landingImages } from './landingImages.js'
import styles from './GuestLandingPage.module.css'

const ProductVisual = ({ imageSrc, label, type = 'analysis' }) => (
  <div className={`${styles.productVisual} ${styles[type]}`}>
    <img src={imageSrc} alt={label} />
  </div>
)

const JobFitVisual = () => (
  <div className={styles.jobFitVisual}>
    <div className={styles.jobFitOverview}>
      <img src={landingImages.jobFitOverview} alt="채용공고 상세 및 PASSED 합격 코칭 과정" />
    </div>
    <div className={styles.jobFitSkills}>
      <img src={landingImages.jobFitSkills} alt="항목별 스킬 매칭률과 강점 및 보완 스킬 분석" />
    </div>
  </div>
)

const serviceSections = [
  {
    key: 'analysis-skills',
    eyebrow: '이력서·자기소개서 분석',
    title: <>이력서와 자기소개서에서<br /><em>주요 스킬</em>을 발견해요</>,
    lead: <>경험, 기술, 성향, 자격증을 구분하고<br /><strong>나를 가장 잘 보여주는 주요 스킬을 선택해요.</strong></>,
    image: landingImages.analysisSkills,
    visualLabel: '이력 분석 및 주요 스킬 화면',
    type: 'skills',
  },
  {
    key: 'recommendations',
    eyebrow: '공고 적합도 분석',
    title: <>추천만 하지 않고<br />왜 나와 맞는지도<br /><em>분석해요</em></>,
    lead: <>공고의 자격요건과 우대사항을<br />내 스킬과 비교해 강점과 부족한<br /><strong>역량을 한눈에 보여드려요.</strong></>,
    customVisual: 'job-fit',
  },
  {
    key: 'roadmap',
    eyebrow: '학습 로드맵',
    title: <>부족한 역량은<br />실행할 수 있는 <em>학습 계획</em>으로</>,
    lead: <>지원하고 싶은 공고에서 부족한 부분을 찾고<br /><strong>지금 필요한 학습을 순서대로 계획해요.</strong></>,
    image: landingImages.roadmap,
    visualLabel: '맞춤 학습 로드맵 화면',
    type: 'roadmap',
  },
  {
    key: 'cover-letter',
    eyebrow: '자기소개서 첨삭',
    title: <>지원 공고에 맞게<br /><em>자기소개서</em>를 더 설득력 있게</>,
    lead: <>공고와 문항에 맞게 작성됐는지 확인하고<br /><strong>구체적인 개선 방향으로 완성도를 높여요.</strong></>,
    image: landingImages.coverLetter,
    visualLabel: '자기소개서 첨삭 화면',
    type: 'coverLetter',
    reverse: true,
  },
]

const GuestLandingPage = () => {
  const pageRef = useRef(null)

  useEffect(() => {
    const elements = [...(pageRef.current?.querySelectorAll('[data-reveal]') ?? [])]
    if (!('IntersectionObserver' in window)) {
      elements.forEach((element) => element.classList.add(styles.visible))
      return undefined
    }

    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return
        entry.target.classList.add(styles.visible)
        observer.unobserve(entry.target)
      })
    }, { threshold: 0.16 })

    elements.forEach((element) => observer.observe(element))
    return () => observer.disconnect()
  }, [])

  return (
    <div className={styles.page} ref={pageRef}>
      <section className={styles.hero} aria-labelledby="landing-title">
        <div className={styles.heroGlow} aria-hidden="true" />
        <div className={styles.heroInner}>
          <div className={styles.heroCopy} data-reveal>
            <h1 id="landing-title">취업 준비의 모든 순간을<br /><em>하나의 흐름으로</em><br />연결하세요</h1>
            <p>이력 분석부터 주요 스킬 발견, 맞춤 채용공고와 학습 계획까지<br />PASSED가 합격을 향한 다음 단계를 함께 준비합니다.</p>
            <div className={styles.heroActions}>
              <Link className={styles.primaryAction} to="/signup">시작하기</Link>
              <Link className={styles.secondaryAction} to="/login">로그인</Link>
            </div>
          </div>
          <div className={styles.heroVisual} data-reveal>
            <ProductVisual imageSrc={landingImages.hero} label="PASSED 서비스 전체 화면" type="recommendations" />
          </div>
        </div>
        <a className={styles.scrollGuide} href="#landing-analysis-skills"><span />서비스 살펴보기</a>
      </section>

      {serviceSections.map((section) => (
        <section className={`${styles.feature} ${section.reverse ? styles.reverse : ''}`} id={`landing-${section.key}`} key={section.key}>
          <div className={styles.featureInner}>
            <div className={styles.featureCopy} data-reveal>
              <span className={styles.eyebrow}>{section.eyebrow}</span>
              <h2>{section.title}</h2>
              <p className={styles.featureLead}>{section.lead}</p>
            </div>
            <div className={styles.featureVisual} data-reveal>
              {section.customVisual === 'job-fit' ? (
                <JobFitVisual />
              ) : (
                <ProductVisual imageSrc={section.image} label={section.visualLabel} type={section.type} />
              )}
            </div>
          </div>
        </section>
      ))}

      <section className={styles.finalCta} aria-labelledby="final-cta-title">
        <div data-reveal>
          <span>PASSED</span>
          <h2 id="final-cta-title">PASSED에서<br />나만의 취업 준비를 이어가세요</h2>
          <Link className={styles.finalLogin} to="/login">로그인하고 시작하기</Link>
          <p className={styles.signupPrompt}>아직 계정이 없나요? <Link to="/signup">회원가입</Link></p>
        </div>
      </section>
    </div>
  )
}

export default GuestLandingPage

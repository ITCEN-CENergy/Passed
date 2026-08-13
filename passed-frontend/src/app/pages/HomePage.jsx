import { Link } from 'react-router-dom'
import { JobPostingBasket } from '../../features/roadmap/components/index.js'

function HomePage() {
  return (
    <section className="page">
      <p className="eyebrow">PASSED</p>
      <h1>취업 준비를 더 자신 있게</h1>
      <p>내 이력과 자기소개서를 한곳에서 관리하고, 지원 준비를 차근차근 이어가세요.</p>
      <div className="homeActions">
        <Link className="button" to="/cover-letter-list">자기소개서 확인하기</Link>
        <Link className="button buttonOutline" to="/recommendations">채용공고 추천받기</Link>
      </div>
      <JobPostingBasket />
    </section>
  )
}

export default HomePage

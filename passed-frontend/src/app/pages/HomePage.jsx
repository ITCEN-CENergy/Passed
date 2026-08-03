import { Link } from 'react-router-dom'

function HomePage() {
  return (
    <section className="page">
      <p className="eyebrow">PASSED</p>
      <h1>홈 페이지</h1>
      <p>React Router 설정이 완료되었습니다.</p>
      <Link className="button" to="/about">
        서비스 소개 보기
      </Link>
    </section>
  )
}

export default HomePage

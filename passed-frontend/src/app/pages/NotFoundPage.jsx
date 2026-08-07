import { Link } from 'react-router-dom'

function NotFoundPage() {
  return (
    <section className="page">
      <p className="eyebrow">404</p>
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>주소가 변경되었거나 존재하지 않는 페이지입니다.</p>
      <Link className="button" to="/">홈으로 돌아가기</Link>
    </section>
  )
}

export default NotFoundPage

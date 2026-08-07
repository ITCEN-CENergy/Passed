import { Link } from 'react-router-dom'
import styles from './Header.module.css'

const Header = ({ logoSrc = '' }) => {
  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link className={styles.brand} to="/" aria-label="Passed 홈">
          <span className={styles.logoFrame}>
            {logoSrc ? (
              <img className={styles.logoImage} src={logoSrc} alt="Passed" />
            ) : (
              <span className={styles.logoPlaceholder} aria-hidden="true">P</span>
            )}
          </span>
          <span className={styles.brandText}>
            <strong>Passed</strong>
            <small>합격 가능성을 높이는 맞춤 취업 파트너</small>
          </span>
        </Link>

        <nav className={styles.actions} aria-label="사용자 메뉴">
          <button className={styles.logout} type="button">로그아웃</button>
          <button className={styles.myPage} type="button">마이페이지</button>
        </nav>
      </div>
    </header>
  )
}

export default Header

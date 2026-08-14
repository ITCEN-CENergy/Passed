import { useEffect } from 'react'
import { Link, NavLink } from 'react-router-dom'
import logo from '../../assets/images/logo.webp'
import useAuthStore from '../../features/auth/model/useAuthStore.js'
import styles from './Header.module.css'

const Header = () => {
  const user = useAuthStore((state) => state.user)
  const isChecking = useAuthStore((state) => state.isChecking)
  const initialize = useAuthStore((state) => state.initialize)
  const logout = useAuthStore((state) => state.logout)

  useEffect(() => {
    void initialize()
  }, [initialize])

  const handleLogout = async () => {
    await logout()
  }

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link className={styles.brand} to="/" aria-label="PASSED 홈">
          <img className={styles.logo} src={logo} alt="PASSED" />
        </Link>

        <nav className={styles.navigation} aria-label="주요 메뉴">
          <div className={styles.serviceLinks}>
            <NavLink to="/job-postings">채용공고 검색</NavLink>
            <NavLink className={({ isActive }) => isActive ? styles.activeServiceLink : undefined} to="/roadmap">학습로드맵</NavLink>
            <Link to="/cover-letter-list">자기소개서 첨삭</Link>
          </div>
          <div className={styles.authLinks}>
            {isChecking ? (
              <span className={styles.authPlaceholder} aria-hidden="true" />
            ) : user ? (
              <>
                <Link className={styles.loginLink} to="/mypage">마이페이지</Link>
                <button className={styles.logoutButton} type="button" onClick={handleLogout}>
                  로그아웃
                </button>
              </>
            ) : (
              <>
                <NavLink className={styles.loginLink} to="/login">로그인</NavLink>
                <NavLink className={styles.signupLink} to="/signup">회원가입</NavLink>
              </>
            )}
          </div>
        </nav>
      </div>
    </header>
  )
}

export default Header

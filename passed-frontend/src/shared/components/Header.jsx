import { useEffect, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import logo from '../../assets/images/logo.webp'
import { ConfirmDialog } from '../../common/components/index.js'
import useAuthStore from '../../features/auth/model/useAuthStore.js'
import styles from './Header.module.css'

const Header = () => {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const isChecking = useAuthStore((state) => state.isChecking)
  const initialize = useAuthStore((state) => state.initialize)
  const logout = useAuthStore((state) => state.logout)
  const [dialog, setDialog] = useState(null)

  useEffect(() => {
    void initialize()
  }, [initialize])

  const handleLogout = async () => {
    await logout()
    setDialog({ type: 'logged-out' })
  }

  const guardMemberPage = (path) => (event) => {
    if (user) return
    event.preventDefault()
    setDialog({ type: 'login-required', returnTo: path })
  }

  const moveToLogin = () => {
    const returnTo = dialog?.returnTo
    setDialog(null)
    navigate('/login', returnTo ? { state: { returnTo } } : undefined)
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
            <NavLink className={({ isActive }) => isActive ? styles.activeServiceLink : undefined} to="/roadmap" onClick={guardMemberPage('/roadmap')}>학습로드맵</NavLink>
            <Link to="/cover-letter-list" onClick={guardMemberPage('/cover-letter-list')}>자기소개서 첨삭</Link>
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
      <ConfirmDialog
        open={dialog?.type === 'login-required'}
        title="로그인이 필요한 기능이에요"
        description="학습로드맵과 자기소개서 첨삭은 로그인 후 이용할 수 있어요.\n로그인 화면으로 이동할까요?"
        cancelText="다음에 하기"
        confirmText="로그인하기"
        closeLabel="로그인 안내창 닫기"
        iconLabel="로그인 안내"
        onCancel={() => setDialog(null)}
        onConfirm={moveToLogin}
      />
      <ConfirmDialog
        open={dialog?.type === 'logged-out'}
        title="로그아웃되었어요"
        description="안전하게 로그아웃되었습니다.\n다시 로그인하시겠어요?"
        cancelText="닫기"
        confirmText="로그인하기"
        closeLabel="로그아웃 안내창 닫기"
        iconLabel="로그아웃 완료"
        onCancel={() => setDialog(null)}
        onConfirm={moveToLogin}
      />
    </header>
  )
}

export default Header

import { Outlet } from 'react-router-dom'
import Header from './Header.jsx'
import styles from '../features/auth/components/AuthForm.module.css'

const AuthLayout = () => (
  <div className={styles.authLayout}>
    <Header />
    <main className={styles.authMain}>
      <Outlet />
    </main>
  </div>
)

export default AuthLayout

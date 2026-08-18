import { Link } from 'react-router-dom'
import styles from './MyPageActionCard.module.css'

const ArrowIcon = () => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none">
    <path d="m9 18 6-6-6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

const MyPageActionCard = ({ description, icon, title, to, tone = 'blue' }) => (
  <Link className={`${styles.card} ${styles[tone]}`} to={to}>
    <span className={styles.icon} aria-hidden="true">{icon}</span>
    <span className={styles.copy}>
      <strong>{title}</strong>
      <span>{description}</span>
    </span>
    <span className={styles.arrow}>
      <ArrowIcon />
    </span>
  </Link>
)

export default MyPageActionCard

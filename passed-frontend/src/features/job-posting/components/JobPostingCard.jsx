import { Link } from 'react-router-dom'
import styles from './JobPostingCard.module.css'

const JobPostingCard = ({ jobPosting, image, to, recommendation }) => (
  <Link className={styles.card} to={to} state={{ image }}>
    <div className={styles.imageWrap}>
      <img src={image} alt="" />
      {recommendation?.rankOrder && (
        <span className={styles.rank}>추천 {recommendation.rankOrder}위</span>
      )}
    </div>
    <div className={styles.content}>
      <p className={styles.company}>{jobPosting.companyName}</p>
      <h2>{jobPosting.title}</h2>
      <div className={styles.meta}>
        <span>{jobPosting.region || '지역 협의'}</span>
        <span>{jobPosting.jobRoleName || '직무 미정'}</span>
      </div>
      <span className={styles.industry}>{jobPosting.industryName || '산업 정보 없음'}</span>
      {recommendation && (
        <div className={styles.scoreRow}>
          <strong>{Math.round(Number(recommendation.totalScore ?? 0) * 100) / 100}점</strong>
          <span>{recommendation.gradeLabel}</span>
        </div>
      )}
    </div>
  </Link>
)

export default JobPostingCard

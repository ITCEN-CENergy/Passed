import styles from './JobPostingDetailContent.module.css'

const SECTION_FIELDS = [
  ['positionDetail', '포지션 소개'],
  ['mainDuty', '주요 업무'],
  ['qualification', '자격 요건'],
  ['preference', '우대 사항'],
  ['disqualification', '지원 제한'],
  ['process', '전형 절차'],
  ['benefit', '복지 및 혜택'],
]

const META_FIELDS = [
  ['careerType', '경력'],
  ['hireType', '고용 형태'],
  ['educationLevel', '학력'],
  ['companySize', '기업 규모'],
]

const splitSentences = (value) => String(value ?? '')
  .split(/[?？]+|\r?\n+|[•●○◦▪︎■◆]+/)
  .map((sentence) => sentence.trim().replace(/^[•·●○◦▪︎■◆\-–—]+\s*/, ''))
  .filter(Boolean)

const paragraphText = (value) => splitSentences(value).join(' ')

const JobPostingDetailContent = ({ jobPosting, image, action, guidance, children }) => (
  <article className={styles.detail}>
    <div className={styles.hero} style={{ '--hero-image': `url("${image}")` }}>
      <img src={image} alt={`${jobPosting.companyName} 회사 이미지`} />
    </div>

    <header className={styles.header}>
      <div>
        <p>{jobPosting.companyName}</p>
        <h1>{jobPosting.title}</h1>
        <div className={styles.location}>
          <span>⌖ {jobPosting.region || '지역 협의'}</span>
          <span>{jobPosting.industryName} <b>›</b> {jobPosting.jobRoleName}</span>
        </div>
      </div>
      {action}
    </header>

    <dl className={`${styles.metaGrid} ${guidance ? styles.metaGridWithGuidance : ''}`}>
      {META_FIELDS.map(([field, label]) => (
        <div key={field}>
          <dt>{label}</dt>
          <dd>{jobPosting[field] || '협의 후 결정'}</dd>
        </div>
      ))}
    </dl>

    {guidance}

    {children}

    <div className={styles.sections}>
      {SECTION_FIELDS.map(([field, label]) => {
        const sentences = splitSentences(jobPosting[field])
        if (!sentences.length) return null
        return (
          <section key={field}>
            <h2>{label}</h2>
            {field === 'positionDetail' ? (
              <p className={styles.paragraph}>{paragraphText(jobPosting[field])}</p>
            ) : (
              <ul>
                {sentences.map((sentence, index) => <li key={`${field}-${index}`}>{sentence}</li>)}
              </ul>
            )}
          </section>
        )
      })}
    </div>
  </article>
)

export default JobPostingDetailContent

import styles from './RecommendationReport.module.css'

const GRADE_LABELS = {
  HIGHLY_RECOMMENDED: '적극 추천',
  RECOMMENDED: '추천',
  CHALLENGING: '도전',
  LOW_MATCH: '준비 후 지원',
}

const TYPE_LABELS = {
  REQUIRED: '자격요건',
  PREFERRED: '우대사항',
  RELATED: '관련 스킬',
}

const normalizeRate = (value) => Math.min(1, Math.max(0, Number(value) || 0))
const percent = (value) => Math.round(normalizeRate(value) * 100)

const matchLabel = (value) => {
  const rate = normalizeRate(value)
  if (rate === 0) return { text: '미충족', tone: 'missing' }
  if (rate === 1) return { text: '충족', tone: 'fulfilled' }
  return { text: '보완 필요', tone: 'partial' }
}

const SkillRow = ({ skill }) => {
  const status = matchLabel(skill.matchRate)
  return (
    <div className={styles.skillRow}>
      <span className={styles.skillName}>
        {skill.isImportant && <span className={styles.star} title="중요 스킬" aria-label="중요 스킬">★</span>}
        {skill.skillName}
      </span>
      <span className={`${styles.status} ${styles[status.tone]}`}>{status.text}</span>
    </div>
  )
}

const HighlightColumn = ({ title, description, skills, emptyMessage, strength }) => (
  <section className={styles.highlightColumn}>
    <div className={styles.highlightHeader}>
      <span className={strength ? styles.strengthIcon : styles.gapIcon}>{strength ? '✓' : '!'}</span>
      <div><h3>{title}</h3><p>{description}</p></div>
    </div>
    <div className={styles.highlightList}>
      {skills?.length ? skills.map((skill) => <SkillRow key={skill.skillId} skill={skill} />) : <p className={styles.empty}>{emptyMessage}</p>}
    </div>
  </section>
)

const RecommendationReport = ({ report, onCreateRoadmap, onReviewCoverLetter }) => (
  <section className={styles.report} aria-labelledby="report-title">
    <div className={styles.reportTitle}>
      <div>
        <h2 id="report-title">나와 채용공고의 적합도 분석</h2>
      </div>
      <div className={styles.reportActions}>
        <button className={styles.roadmapButton} type="button" onClick={onCreateRoadmap}>
          학습 로드맵 담기
        </button>
        <button className={styles.coverLetterButton} type="button" onClick={onReviewCoverLetter}>
          자소서 첨삭
        </button>
      </div>
    </div>

    <div className={styles.summary}>
      <div className={styles.grade}>
        <span>추천 등급</span>
        <strong>{GRADE_LABELS[report.grade] ?? report.grade}</strong>
      </div>
      <div className={styles.totalScore}>
        <span>종합 점수</span>
        <strong>{Math.round(Number(report.totalScore ?? 0))}<small>점</small></strong>
      </div>
      <p>{report.reason || '보유 스킬과 공고의 요구 역량을 종합적으로 분석한 결과입니다.'}</p>
    </div>

    <div className={styles.groupSection}>
      <div className={styles.sectionHeading}>
        <h3>항목별 스킬 매칭률</h3>
        <p>상세보기를 열어 스킬별 충족 여부를 확인하세요.</p>
      </div>
      <div className={styles.groupGrid}>
        {report.skillGroups?.map((group) => {
          const rate = percent(group.levelMatchRate)
          return (
            <article className={styles.groupCard} key={group.skillType}>
              <div className={styles.chart} style={{ '--rate': `${rate * 3.6}deg` }}>
                <div><strong>{rate}%</strong><span>{group.ownedCount}/{group.totalCount}개</span></div>
              </div>
              <h4>{TYPE_LABELS[group.skillType] ?? group.skillType}</h4>
              <details>
                <summary>상세보기 <span aria-hidden="true">⌄</span></summary>
                <div className={styles.skillList}>
                  {group.skills?.length ? group.skills.map((skill) => <SkillRow key={skill.skillId} skill={skill} />) : <p className={styles.empty}>분석할 스킬이 없습니다.</p>}
                </div>
              </details>
            </article>
          )
        })}
      </div>
    </div>

    <p className={styles.importantGuide}><span aria-hidden="true">★</span> 별표는 사용자가 중요 표시한 스킬이에요.</p>
    <div className={styles.highlightGrid}>
      <HighlightColumn title="강점 스킬" description="공고와 잘 맞는 보유 역량이에요." skills={report.topStrengthSkills} emptyMessage="표시할 강점 스킬이 없습니다." strength />
      <HighlightColumn title="보완 스킬" description="지원 전 보완하면 경쟁력이 높아져요." skills={report.topGapSkills} emptyMessage="보완이 필요한 스킬이 없습니다." />
    </div>
  </section>
)

export { GRADE_LABELS }
export default RecommendationReport

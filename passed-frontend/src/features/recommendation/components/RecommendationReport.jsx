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

const RoadmapIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4.5h9a3 3 0 0 1 3 3v12H8a3 3 0 0 1-3-3v-12Z" /><path d="M8 4.5v15M11 9h3M11 13h3" /></svg>
)

const CoverLetterIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4h10l4 4v12H5V4Z" /><path d="M15 4v4h4M8 12h8M8 16h5" /></svg>
)

const DetailChevron = () => (
  <svg viewBox="0 0 20 14" aria-hidden="true"><path d="m2 4 8 7 8-7" /></svg>
)

const RecommendationReport = ({ report, onCreateRoadmap, onReviewCoverLetter, roadmapAdded = false }) => (
  <section className={styles.report} aria-labelledby="report-title">
    <div className={styles.reportTitle}>
      <div>
        <h2 id="report-title">나와 채용공고의 적합도 분석</h2>
        <p>분석 결과를 확인하고 바로 다음 합격 준비로 이어가세요.</p>
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
                <summary>상세보기 <span aria-hidden="true"><DetailChevron /></span></summary>
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

    <section className={styles.nextStepPanel} aria-labelledby="next-step-title">
      <div className={styles.nextStepCopy}>
        <h3 id="next-step-title">맞춤형 취업 코칭</h3>
        <p>부족한 역량은 학습 계획으로 채우고, 이 공고에 맞는 자기소개서로 지원 준비를 완성할 수 있어요.</p>
      </div>
      <div className={styles.nextActions}>
        <button className={styles.roadmapAction} type="button" onClick={onCreateRoadmap} disabled={roadmapAdded}>
          <span className={styles.actionIcon}><RoadmapIcon /></span>
          <span className={styles.actionText}>
            <small>보완점은 채우고, 강점을 강조하기</small>
            <strong>{roadmapAdded ? '학습 로드맵 담기 완료' : '학습 로드맵 담기'}</strong>
          </span>
        </button>
        <button className={styles.coverLetterAction} type="button" onClick={onReviewCoverLetter}>
          <span className={styles.actionIcon}><CoverLetterIcon /></span>
          <span className={styles.actionText}>
            <small>공고에 맞춰 자기소개서 다듬기</small>
            <strong>자기소개서 첨삭</strong>
          </span>
        </button>
      </div>
    </section>
  </section>
)

export { GRADE_LABELS }
export default RecommendationReport

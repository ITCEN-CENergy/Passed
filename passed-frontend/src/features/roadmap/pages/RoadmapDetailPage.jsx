import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ConfirmDialog, PageLoading } from '../../../common/components/index.js'
import { getJobPostingImage } from '../../job-posting/utils/jobPostingImages.js'
import { applyRoadmapReplan, changeMilestoneCompletion, deleteRoadmap, getRoadmap, previewRoadmapReplan, updateRoadmapStudyTime } from '../api/index.js'
import styles from './RoadmapDetailPage.module.css'

const labels = {
  ACTIVE: '진행 중', COMPLETED: '완료', CREATING: '생성 중', FAILED: '생성 실패',
  TECHNICAL_SKILL: '기술 역량', EXPERIENCE: '경험', BEHAVIORAL_TRAIT: '행동 특성', CERTIFICATION: '자격',
  REQUIRED: '공통', PREFERRED: '선택', RELATED: '관련', CONCEPT: '개념', PRACTICE: '실습', PROJECT: '프로젝트', ASSESSMENT: '평가', CERTIFICATION_TYPE: '자격',
  BEGINNER: '초급', INTERMEDIATE: '중급', ADVANCED: '고급', NOT_STARTED: '시작 전', IN_PROGRESS: '진행 중', COMPLETED_MILESTONE: '완료',
}
const fmtDate = (value) => value ? String(value).slice(0, 10).replaceAll('-', '.') : '-'
const fmtHours = (minutes) => `${Number.isInteger((minutes || 0) / 60) ? (minutes || 0) / 60 : ((minutes || 0) / 60).toFixed(1)}시간`
const fmtRemainingWeeks = (endDate) => {
  if (!endDate) return null
  const end = new Date(`${String(endDate).slice(0, 10)}T00:00:00`)
  const today = new Date(); today.setHours(0, 0, 0, 0)
  return Math.max(0, Math.ceil((end - today) / 86400000 / 7))
}
const fmtDailyStudyTime = (minutes) => Number(minutes) % 60
  ? `${Math.floor(Number(minutes) / 60) ? `${Math.floor(Number(minutes) / 60)}시간 ` : ''}${Number(minutes) % 60}분`
  : `${Number(minutes) / 60}시간`
const dailyStudyTimeOptions = Array.from({ length: 16 }, (_, index) => (index + 1) * 30)
const progress = (value) => Math.min(100, Math.max(0, Number(value) || 0))
const resourceTypeLabels = {
  WEB_RESOURCE: '웹 자료',
  BOOK: '책',
  KMOOC_COURSE: '강의',
}
const resourceTypeLabel = (resource) => resource.provider === '인프런'
  ? '강의'
  : resourceTypeLabels[resource.resourceType] || '학습자료'
const skillGroups = [
  { type: 'REQUIRED', title: '공통 역량', description: '선택한 모든 공고에서 필수 또는 우대하는 역량' },
  { type: 'PREFERRED', title: '선택 역량', description: '일부 공고에서 필수 또는 우대하는 역량' },
  { type: 'RELATED', title: '관련 역량', description: '직무 수행에 도움이 되는 보완 역량' },
]

const dateKey = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const activityLevel = (count) => count === 0 ? 0 : count === 1 ? 1 : count === 2 ? 2 : count === 3 ? 3 : 4
const activityTooltip = (key, count) => {
  const [, month, day] = key.split('-').map(Number)
  return count > 0
    ? `${month}월 ${day}일에 마일스톤 ${count}개 완료`
    : `${month}월 ${day}일에는 완료한 마일스톤이 없어요`
}

const LearningActivity = ({ activities = [] }) => {
  const counts = new Map(activities.map(activity => [String(activity.date).slice(0, 10), Number(activity.completedMilestoneCount) || 0]))
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const firstDay = new Date(today); firstDay.setDate(today.getDate() - today.getDay() - (52 * 7))
  const days = Array.from({ length: 53 * 7 }, (_, index) => {
    const date = new Date(firstDay); date.setDate(firstDay.getDate() + index)
    const key = dateKey(date)
    return { key, count: date > today ? null : (counts.get(key) || 0) }
  })
  const activeDays = activities.filter(activity => Number(activity.completedMilestoneCount) > 0).length
  const completedCount = activities.reduce((sum, activity) => sum + (Number(activity.completedMilestoneCount) || 0), 0)
  return <section className={styles.activity} aria-labelledby="learning-activity-title">
    <header><div><h2 id="learning-activity-title">학습 기록</h2><p>마일스톤을 완료한 날이 진할수록 더 많이 학습한 날이에요.</p></div><strong>{activeDays}일 학습 · {completedCount}개 완료</strong></header>
    <div className={styles.activityChart}>
      <div className={styles.weekdayLabels} aria-hidden="true"><span>월</span><span>수</span><span>금</span></div>
      <div className={styles.activityGrid}>{days.map(day => <span className={`${styles.activityCell} ${day.count === null ? styles.activityFuture : styles[`activityLevel${activityLevel(day.count)}`]}`} data-tooltip={day.count === null ? undefined : activityTooltip(day.key, day.count)} aria-label={day.count === null ? undefined : activityTooltip(day.key, day.count)} tabIndex={day.count === null ? undefined : 0} key={day.key} />)}</div>
    </div>
    <footer aria-label="학습량 색상 범례"><span>적음</span>{[0, 1, 2, 3, 4].map(level => <i className={`${styles.activityCell} ${styles[`activityLevel${level}`]}`} key={level} />)}<span>많음</span></footer>
  </section>
}

const ProgressRing = ({ value }) => <div className={styles.ring} style={{ '--progress': `${progress(value) * 3.6}deg` }}><div><strong>{progress(value).toFixed(progress(value) % 1 ? 1 : 0)}%</strong><span>전체 진행률</span></div></div>

const ProgressSummary = ({ value, skills = [] }) => {
  const milestones = skills.flatMap(skill => (skill.milestones || []).map(milestone => ({ ...milestone, skillName: skill.standardCompetencyName })))
  const completedCount = milestones.filter(milestone => milestone.status === 'COMPLETED').length
  const remainingCount = Math.max(0, milestones.length - completedCount)
  const remainingMilestones = milestones.filter(milestone => milestone.status !== 'COMPLETED')
  const remainingMinutes = remainingMilestones.reduce((sum, milestone) => sum + (Number(milestone.estimatedMinutes) || 0), 0)
  const nextMilestone = remainingMilestones[0]
  return <aside className={styles.progressSummary} aria-label="로드맵 진행 요약">
    <ProgressRing value={value} />
    <div className={styles.milestoneSummary}>
      <span><small>완료한 마일스톤</small><strong>{completedCount}<em>개</em></strong></span>
      <i aria-hidden="true" />
      <span><small>남은 마일스톤</small><strong>{remainingCount}<em>개</em></strong></span>
    </div>
    <div className={styles.nextMilestone}>
      <div className={styles.nextMilestoneHeading}><span>다음 학습 목표</span>{remainingCount > 0 && <small>남은 {fmtHours(remainingMinutes)}</small>}</div>
      {nextMilestone
        ? <><strong>{nextMilestone.title}</strong><p>{nextMilestone.skillName}</p><span className={styles.nextMilestoneTime}>예상 {fmtHours(nextMilestone.estimatedMinutes)}</span></>
        : <p className={styles.allMilestonesDone}>모든 마일스톤을 완료했어요!</p>}
    </div>
  </aside>
}

const StudyTimeSelect = ({ value, disabled, onChange }) => {
  const [open, setOpen] = useState(false)
  const rootRef = useRef(null)
  const optionRefs = useRef([])
  const selectedIndex = Math.max(0, dailyStudyTimeOptions.indexOf(Number(value)))

  useEffect(() => {
    if (!open) return undefined
    const close = (event) => {
      if (!rootRef.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [open])

  const openAndFocus = (index = selectedIndex) => {
    if (disabled) return
    setOpen(true)
    requestAnimationFrame(() => optionRefs.current[index]?.focus())
  }
  const choose = (minutes) => {
    setOpen(false)
    if (minutes !== Number(value)) onChange(minutes)
    requestAnimationFrame(() => rootRef.current?.querySelector('button')?.focus())
  }
  const handleTriggerKeyDown = (event) => {
    if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
      event.preventDefault()
      openAndFocus(event.key === 'ArrowUp' ? dailyStudyTimeOptions.length - 1 : selectedIndex)
    }
  }
  const handleOptionKeyDown = (event, index) => {
    if (event.key === 'Escape') {
      event.preventDefault(); setOpen(false); rootRef.current?.querySelector('button')?.focus()
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const direction = event.key === 'ArrowDown' ? 1 : -1
      optionRefs.current[(index + direction + dailyStudyTimeOptions.length) % dailyStudyTimeOptions.length]?.focus()
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault(); optionRefs.current[event.key === 'Home' ? 0 : dailyStudyTimeOptions.length - 1]?.focus()
    }
  }

  return <div className={styles.studyTimeSelect} ref={rootRef}>
    <button className={styles.studyTimeTrigger} type="button" aria-label="하루 학습시간" aria-haspopup="listbox" aria-expanded={open} disabled={disabled} onClick={() => open ? setOpen(false) : openAndFocus()} onKeyDown={handleTriggerKeyDown}>
      <span>{fmtDailyStudyTime(value)}</span><svg className={styles.studyTimeChevron} aria-hidden="true" viewBox="0 0 24 24"><path d="m6 9 6 6 6-6" /></svg>
    </button>
    {open && <div className={styles.studyTimeMenu} role="listbox" aria-label="하루 학습시간 선택">
      {dailyStudyTimeOptions.map((minutes, index) => <button className={`${styles.studyTimeOption} ${minutes === Number(value) ? styles.selectedStudyTime : ''}`} type="button" role="option" aria-selected={minutes === Number(value)} tabIndex={-1} ref={(element) => { optionRefs.current[index] = element }} onClick={() => choose(minutes)} onKeyDown={(event) => handleOptionKeyDown(event, index)} key={minutes}>
        <span>{fmtDailyStudyTime(minutes)}</span>{minutes === Number(value) && <svg aria-hidden="true" viewBox="0 0 24 24"><path d="m5 12.5 4.2 4.2L19 7" /></svg>}
      </button>)}
    </div>}
  </div>
}

const Milestone = ({ item, onToggle, busy }) => {
  const complete = item.status === 'COMPLETED'
  const [resourcesOpen, setResourcesOpen] = useState(true)
  return <article className={styles.milestone}>
    <button className={`${styles.check} ${complete ? styles.checked : ''}`} type="button" disabled={busy} onClick={() => onToggle(item, !complete)} aria-label={`${item.title} ${complete ? '완료 취소' : '완료 처리'}`}>{complete && <svg aria-hidden="true" viewBox="0 0 24 24"><path d="m5 12.5 4.2 4.2L19 7" /></svg>}</button>
    <div className={styles.milestoneBody}>
      <div className={styles.meta}>{labels[item.milestoneType] || item.milestoneType}<i />{labels[item.difficulty] || item.difficulty}<i /><b className={item.required ? styles.requiredMilestone : styles.optionalMilestone} title={item.required ? '역량 학습에 필요한 핵심 단계' : '필요에 따라 추가하는 보충 단계'}>{item.required ? '핵심' : '보충'}</b></div>
      <h4>{item.title}</h4><p>{item.description}</p>
      {item.learningObjective && <p className={styles.detail}>학습 목표 · {item.learningObjective}</p>}
      {item.completionCriteria && <p className={styles.detail}>완료 기준 · {item.completionCriteria}</p>}
    </div>
    <div className={styles.milestoneSide}><span className={complete ? styles.done : styles.doing}>{labels[item.status === 'COMPLETED' ? 'COMPLETED_MILESTONE' : item.status] || item.status}</span><small>예상시간 <strong>{fmtHours(item.estimatedMinutes)}</strong></small></div>
    {!!item.learningResources?.length && <div className={styles.resources}>
        <button className={styles.resourceHeading} type="button" aria-expanded={resourcesOpen} onClick={() => setResourcesOpen(value => !value)}><span><strong>추천 학습자료</strong><b>{item.learningResources.length}개</b></span><i aria-hidden="true">{resourcesOpen ? '⌃' : '⌄'}</i></button>
        {resourcesOpen && <div className={styles.resourceList}>{item.learningResources.map((resource) => <article className={styles.resourceCard} key={resource.resourceId}>
          <div className={styles.resourceInfo}>
            <div><span className={styles.resourceType}>{resourceTypeLabel(resource)}</span></div>
            <h5>{resource.title}</h5>
            {resource.provider && <p>{resource.provider}</p>}
          </div>
          <a className={styles.resourceButton} href={resource.url} target="_blank" rel="noopener noreferrer">학습하기</a>
        </article>)}</div>}
    </div>}
  </article>
}

const SkillCard = ({ skill, groupOrder, onToggle, busyId, anchorId, openRequest }) => {
  const [open, setOpen] = useState(false)
  useEffect(() => {
    if (openRequest?.anchorId === anchorId) setOpen(true)
  }, [anchorId, openRequest])
  return <section className={styles.skill} id={anchorId} data-skill-anchor tabIndex="-1">
    <header><span className={`${styles.order} ${styles[skill.requirementType?.toLowerCase()]}`}>{groupOrder}</span><div className={styles.skillTitle}><div className={styles.meta}>그룹 내 추천 순서 <i /> {labels[skill.category] || skill.category}</div><h3>{skill.standardCompetencyName}</h3></div></header>
    <div className={styles.skillStats}><span>{skill.frequency}개 공고에서 요구</span><span>예상 학습시간<strong>{fmtHours(skill.estimatedMinutes)}</strong></span><span>진행률<strong>{progress(skill.progressRate).toFixed(0)}%</strong><i><b style={{ width: `${progress(skill.progressRate)}%` }} /></i></span></div>
    <div className={styles.learningStages}>
      <button className={styles.fold} type="button" onClick={() => setOpen(value => !value)}><strong>학습 단계 {skill.milestones?.length || 0}개</strong><span>{open ? '⌃' : '⌄'}</span></button>
      {open && <div className={styles.milestones}>{skill.milestones?.map(item => <Milestone key={item.milestoneId} item={item} onToggle={onToggle} busy={busyId === item.milestoneId} />)}</div>}
    </div>
  </section>
}

const RoadmapDetailPage = () => {
  const { roadmapId } = useParams(); const navigate = useNavigate()
  const [roadmap, setRoadmap] = useState(null); const [error, setError] = useState(''); const [busyId, setBusyId] = useState(null); const [actionBusy, setActionBusy] = useState(false); const [isReplanning, setIsReplanning] = useState(false)
  const [activeSkillId, setActiveSkillId] = useState(null); const contentRef = useRef(null)
  const [openSkillRequest, setOpenSkillRequest] = useState(null)
  const [dialog, setDialog] = useState(null); const [replanPreview, setReplanPreview] = useState(null)
  const [studyTimeBusy, setStudyTimeBusy] = useState(false)
  const load = useCallback(async (signal) => { try { setRoadmap(await getRoadmap(roadmapId, { signal })); setError('') } catch (e) { if (e?.name !== 'AbortError') setError(e.message) } }, [roadmapId])
  useEffect(() => { const controller = new AbortController(); void load(controller.signal); return () => controller.abort() }, [load])
  useEffect(() => {
    const anchors = [...(contentRef.current?.querySelectorAll('[data-skill-anchor]') || [])]
    if (!anchors.length) return undefined
    setActiveSkillId(current => current || anchors[0].id)
    const observer = new IntersectionObserver((entries) => {
      const visible = entries.filter(entry => entry.isIntersecting).sort((a, b) => Math.abs(a.boundingClientRect.top) - Math.abs(b.boundingClientRect.top))
      if (visible[0]) setActiveSkillId(visible[0].target.id)
    }, { rootMargin: '-96px 0px -58% 0px', threshold: 0 })
    anchors.forEach(anchor => observer.observe(anchor))
    return () => observer.disconnect()
  }, [roadmap?.skills])
  const moveToSkill = (anchorId) => {
    setActiveSkillId(anchorId)
    setOpenSkillRequest({ anchorId, requestedAt: Date.now() })
    document.getElementById(anchorId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
  const toggle = async (item, completed) => { setBusyId(item.milestoneId); try { await changeMilestoneCompletion(item.milestoneId, completed); await load() } catch (e) { setError(e.message) } finally { setBusyId(null) } }
  const remove = async () => { setActionBusy(true); try { await deleteRoadmap(roadmapId); navigate('/roadmap') } catch (e) { setError(e.message); setActionBusy(false); setDialog(null) } }
  const prepareReplan = async () => { setDialog(null); setActionBusy(true); setIsReplanning(true); setError(''); try { const preview = await previewRoadmapReplan(roadmapId); setReplanPreview(preview); setDialog('replan-result') } catch (e) { setError(e.message) } finally { setIsReplanning(false); setActionBusy(false) } }
  const applyReplan = async () => { setDialog(null); setActionBusy(true); setIsReplanning(true); try { await applyRoadmapReplan(roadmapId, replanPreview.replanToken); await load(); setReplanPreview(null) } catch (e) { setError(e.message) } finally { setIsReplanning(false); setActionBusy(false) } }
  const changeStudyTime = async (dailyStudyMinutes) => {
    const previousMinutes = roadmap.dailyStudyMinutes || 60
    setRoadmap(current => ({ ...current, dailyStudyMinutes })); setStudyTimeBusy(true); setError('')
    try { setRoadmap(await updateRoadmapStudyTime(roadmapId, dailyStudyMinutes)) }
    catch (e) { setRoadmap(current => ({ ...current, dailyStudyMinutes: previousMinutes })); setError(e.message) }
    finally { setStudyTimeBusy(false) }
  }
  if (error && !roadmap) return <main className={styles.page}><div className={styles.state}><p>{error}</p><Link to="/roadmap">로드맵 목록</Link></div></main>
  if (!roadmap) return <main className={styles.page}><div className={styles.state} role="status">로드맵을 불러오고 있어요.</div></main>
  if (isReplanning) return <main className={styles.page}><PageLoading title="학습 일정을 재계획하고 있어요" description="남은 학습 단계를 분석해 현재 일정에 맞게 다시 구성합니다." ariaLabel="학습 일정 재계획 중" /></main>
  const groupedSkills = skillGroups.map(group => ({
    ...group,
    skills: (roadmap.skills || []).filter(skill => skill.requirementType === group.type),
  })).filter(group => group.skills.length)
  const orderedSkills = groupedSkills.flatMap(group => group.skills)
  const completedStudyMinutes = (roadmap.skills || []).flatMap(skill => skill.milestones || []).reduce((sum, milestone) => milestone.status === 'COMPLETED' ? sum + (Number(milestone.estimatedMinutes) || 0) : sum, 0)
  return <main className={styles.page}>
    <div className={styles.toolbar}><Link to="/roadmap">로드맵 목록 보기</Link></div>
    {error && <div className={styles.error} role="alert">{error}<button onClick={() => setError('')}>×</button></div>}
    <div className={styles.contentLayout} ref={contentRef}>
      <div className={styles.mainContent}>
        <section className={styles.summary}>
          <div className={styles.summaryBody}>
            <div className={styles.summaryTitle}><h1>{roadmap.title}</h1><span className={styles[`roadmapStatus${roadmap.status}`]}>{labels[roadmap.status] || roadmap.status}</span></div>
            <p>연결된 채용공고 {roadmap.jobPostings?.length ?? roadmap.jobPostingIds?.length ?? 0}개 <i /> 최근 수정 {fmtDate(roadmap.updatedAt)}</p>
            {!!roadmap.jobPostings?.length && <div className={styles.linkedPostings}>{roadmap.jobPostings.map(posting => <Link className={styles.linkedPosting} to={`/job-postings/${posting.jobPostingId}`} state={{ image: getJobPostingImage(posting.jobPostingId) }} key={posting.jobPostingId}><img src={getJobPostingImage(posting.jobPostingId)} alt="" /><span><small>{posting.companyName}</small><strong>{posting.title}</strong></span></Link>)}</div>}
            <div className={styles.scheduleSection}>
              <div className={styles.scheduleHeading}>
                <div><h2>나의 학습 일정</h2></div>
                <p>하루 학습시간을 바꾸면 예상 완료 기간이 자동으로 조정돼요.</p>
              </div>
              <div className={styles.scheduleFlow}>
                <article className={styles.scheduleMetric}>
                  <span className={styles.scheduleStep}>1. 나의 학습시간</span>
                  <p>완료한 마일스톤 기준 진행 시간과 총 학습시간</p>
                  <strong className={styles.studyTimeProgress}><span>{fmtHours(completedStudyMinutes)}</span><small>/</small><span>{fmtHours(roadmap.totalEstimatedMinutes)}</span></strong>
                </article>
                <span className={styles.scheduleArrow} aria-hidden="true"></span>
                <article className={`${styles.scheduleMetric} ${styles.dailyScheduleMetric}`}>
                  <span className={styles.scheduleStep}>2. 나의 하루 학습시간</span>
                  <p>내가 매일 공부할 수 있는 시간을 선택하세요.</p>
                  <div className={styles.studyTimeControl}>
                    <StudyTimeSelect value={roadmap.dailyStudyMinutes || 60} disabled={studyTimeBusy} onChange={changeStudyTime} />
                  </div>
                </article>
                <span className={styles.scheduleArrow} aria-hidden="true"></span>
                <article className={`${styles.scheduleMetric} ${styles.periodScheduleMetric}`} aria-live="polite">
                  <span className={styles.scheduleStep}>3. 예상 완료 기간</span>
                  <p>{studyTimeBusy ? '완료 기간을 다시 계산하고 있어요…' : `하루 ${fmtDailyStudyTime(roadmap.dailyStudyMinutes || 60)} 학습 기준`}</p>
                  <strong>{fmtRemainingWeeks(roadmap.estimatedEndDate)}주</strong>
                  <div className={styles.studyDates}><time>{fmtDate(roadmap.createdAt)}</time>~<time>{fmtDate(roadmap.estimatedEndDate)}</time></div>
                </article>
              </div>
            </div>
          </div>
          <ProgressSummary value={roadmap.progressRate} skills={roadmap.skills} />
        </section>
        {roadmap.replanRecommended && <section className={styles.warning}><strong>⚠</strong><div><h2>학습 일정이 예정보다 {roadmap.delayDays}일 늦어지고 있어요</h2><p>남은 학습 단계를 현재 일정에 맞게 다시 구성할 수 있습니다.</p></div><button type="button" disabled={actionBusy} onClick={() => setDialog('replan')}>일정 재계획</button></section>}
        <LearningActivity activities={roadmap.learningActivities} />
        <div className={styles.skillGuide}><strong>역량 중요도를 먼저 확인해 보세요</strong><p>공통·선택·관련 순으로 구분했고, 각 그룹 안에서는 공고 요구 빈도와 역량 격차를 반영한 추천 순서로 보여줘요.</p></div>
        <div className={styles.skillGroups}>{groupedSkills.map(group => <section className={`${styles.skillGroup} ${styles[`${group.type.toLowerCase()}Group`]}`} key={group.type}>
          <header className={styles.skillGroupHeader}><div><span>{labels[group.type]}</span><h2>{group.title}</h2></div><strong>{group.skills.length}개</strong><p>{group.description}</p></header>
          <div className={styles.skills}>{group.skills.map((skill, index) => <SkillCard key={skill.roadmapSkillId} skill={skill} groupOrder={index + 1} anchorId={`roadmap-skill-${skill.roadmapSkillId}`} openRequest={openSkillRequest} onToggle={toggle} busyId={busyId} />)}</div>
        </section>)}</div>
        <section className={styles.dangerZone} aria-labelledby="roadmap-delete-title">
          <div><h2 id="roadmap-delete-title">로드맵 삭제</h2><p>삭제한 로드맵과 학습 기록은 복구할 수 없습니다.</p></div>
          <button type="button" disabled={actionBusy} onClick={() => setDialog('delete')}>로드맵 삭제</button>
        </section>
      </div>
      {!!orderedSkills.length && <aside className={styles.skillNavigation} aria-label="스킬 바로가기">
        <div className={styles.skillNavigationHeader}><h2>스킬 바로가기</h2><span>{orderedSkills.length}</span></div>
        <nav>{orderedSkills.map((skill) => {
          const anchorId = `roadmap-skill-${skill.roadmapSkillId}`
          const active = activeSkillId === anchorId
          return <button key={skill.roadmapSkillId} className={active ? styles.activeSkillLink : ''} type="button" title={skill.standardCompetencyName} aria-current={active ? 'location' : undefined} onClick={() => moveToSkill(anchorId)}><span className={styles[`nav${skill.requirementType}`]}>{labels[skill.requirementType]}</span><strong>{skill.standardCompetencyName}</strong></button>
        })}</nav>
      </aside>}
    </div>
    <ConfirmDialog open={dialog === 'delete'} title="로드맵을 삭제할까요?" description="삭제한 로드맵과 학습 기록은 복구할 수 없습니다." cancelText="취소" confirmText="삭제하기" loadingText="삭제 중…" closeLabel="로드맵 삭제 확인창 닫기" iconLabel="삭제 주의" isLoading={actionBusy} onCancel={() => setDialog(null)} onConfirm={remove} />
    <ConfirmDialog open={dialog === 'replan'} title="학습 일정을 재계획할까요?" description="남은 학습 단계를 분석해 현재 일정에 맞게 다시 구성합니다." cancelText="취소" confirmText="재계획 시작" closeLabel="일정 재계획 확인창 닫기" iconLabel="일정 재계획 안내" onCancel={() => setDialog(null)} onConfirm={prepareReplan} />
    <ConfirmDialog open={dialog === 'replan-result'} title="일정 재계획이 준비되었어요" description={replanPreview?.summary || '남은 학습 단계를 핵심 과정으로 압축했어요.'} cancelText="취소" confirmText="재계획 적용하기" closeLabel="재계획 결과창 닫기" iconLabel="재계획 완료" onCancel={() => { setDialog(null); setReplanPreview(null) }} onConfirm={applyReplan}>
      <div className={styles.replanComparison}><span>기존 완료 예정일<strong>{fmtDate(replanPreview?.previousEstimatedEndDate)}</strong></span><b>→</b><span>새 완료 예정일<strong>{fmtDate(replanPreview?.replannedEstimatedEndDate)}</strong></span></div>
    </ConfirmDialog>
  </main>
}
export default RoadmapDetailPage

import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ConfirmDialog, PageLoading } from '../../../common/components/index.js'
import { applyRoadmapReplan, changeMilestoneCompletion, deleteRoadmap, getRoadmap, previewRoadmapReplan } from '../api/index.js'
import styles from './RoadmapDetailPage.module.css'

const labels = {
  ACTIVE: '진행 중', COMPLETED: '완료', CREATING: '생성 중', FAILED: '생성 실패',
  TECHNICAL_SKILL: '기술 역량', EXPERIENCE: '경험', BEHAVIORAL_TRAIT: '행동 특성', CERTIFICATION: '자격',
  REQUIRED: '공통 필수', PREFERRED: '선택', RELATED: '관련', CONCEPT: '개념', PRACTICE: '실습', PROJECT: '프로젝트', ASSESSMENT: '평가', CERTIFICATION_TYPE: '자격',
  BEGINNER: '초급', INTERMEDIATE: '중급', ADVANCED: '고급', NOT_STARTED: '시작 전', IN_PROGRESS: '진행 중', COMPLETED_MILESTONE: '완료',
}
const fmtDate = (value) => value ? String(value).slice(0, 10).replaceAll('-', '.') : '-'
const fmtHours = (minutes) => `${Number.isInteger((minutes || 0) / 60) ? (minutes || 0) / 60 : ((minutes || 0) / 60).toFixed(1)}시간`
const progress = (value) => Math.min(100, Math.max(0, Number(value) || 0))
const skillGroups = [
  { type: 'REQUIRED', title: '공통 필수 역량', description: '선택한 모든 공고에서 필수로 요구하는 역량' },
  { type: 'PREFERRED', title: '선택 역량', description: '일부 공고에서 필수 또는 우대하는 역량' },
  { type: 'RELATED', title: '관련 역량', description: '직무 수행에 도움이 되는 보완 역량' },
]

const ProgressRing = ({ value }) => <div className={styles.ring} style={{ '--progress': `${progress(value) * 3.6}deg` }}><div><strong>{progress(value).toFixed(progress(value) % 1 ? 1 : 0)}%</strong><span>전체 진행률</span></div></div>

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
            <div><span className={styles.resourceType}>{resource.resourceType || '학습자료'}</span></div>
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
  if (error && !roadmap) return <main className={styles.page}><div className={styles.state}><p>{error}</p><Link to="/roadmap">로드맵 목록</Link></div></main>
  if (!roadmap) return <main className={styles.page}><div className={styles.state} role="status">로드맵을 불러오고 있어요.</div></main>
  if (isReplanning) return <main className={styles.page}><PageLoading title="학습 일정을 재계획하고 있어요" description="남은 학습 단계를 분석해 현재 일정에 맞게 다시 구성합니다." ariaLabel="학습 일정 재계획 중" /></main>
  const groupedSkills = skillGroups.map(group => ({
    ...group,
    skills: (roadmap.skills || []).filter(skill => skill.requirementType === group.type),
  })).filter(group => group.skills.length)
  return <main className={styles.page}>
    <div className={styles.toolbar}><Link to="/roadmap">← <span>로드맵 목록</span></Link><button type="button" disabled={actionBusy} onClick={() => setDialog('delete')}>⌫ 로드맵 삭제</button></div>
    {error && <div className={styles.error} role="alert">{error}<button onClick={() => setError('')}>×</button></div>}
    <div className={styles.contentLayout} ref={contentRef}>
      <div className={styles.mainContent}>
        <section className={styles.summary}><div className={styles.summaryBody}><div className={styles.summaryTitle}><h1>{roadmap.title}</h1><span className={styles[`roadmapStatus${roadmap.status}`]}>{labels[roadmap.status] || roadmap.status}</span></div><p>연결된 채용공고 {roadmap.jobPostingIds?.length || 0}개 <i /> 최근 수정 {fmtDate(roadmap.updatedAt)}</p><div className={styles.schedule}><span>예상 학습시간<strong>{fmtHours(roadmap.totalEstimatedMinutes)}</strong></span><span>최초 완료 예정일<strong>{fmtDate(roadmap.baselineEndDate)}</strong></span><span>현재 완료 예정일<strong>{fmtDate(roadmap.estimatedEndDate)}</strong></span></div></div><ProgressRing value={roadmap.progressRate} /></section>
        {roadmap.replanRecommended && <section className={styles.warning}><strong>⚠</strong><div><h2>학습 일정이 예정보다 {roadmap.delayDays}일 늦어지고 있어요</h2><p>남은 학습 단계를 현재 일정에 맞게 다시 구성할 수 있습니다.</p></div><button type="button" disabled={actionBusy} onClick={() => setDialog('replan')}>일정 재계획</button></section>}
        <div className={styles.skillGuide}><strong>역량 중요도를 먼저 확인해 보세요</strong><p>공통 필수·선택·관련 순으로 구분했고, 각 그룹 안에서는 공고 요구 빈도와 역량 격차를 반영한 추천 순서로 보여줘요.</p></div>
        <div className={styles.skillGroups}>{groupedSkills.map(group => <section className={`${styles.skillGroup} ${styles[`${group.type.toLowerCase()}Group`]}`} key={group.type}>
          <header className={styles.skillGroupHeader}><div><span>{labels[group.type]}</span><h2>{group.title}</h2></div><strong>{group.skills.length}개</strong><p>{group.description}</p></header>
          <div className={styles.skills}>{group.skills.map((skill, index) => <SkillCard key={skill.roadmapSkillId} skill={skill} groupOrder={index + 1} anchorId={`roadmap-skill-${skill.roadmapSkillId}`} openRequest={openSkillRequest} onToggle={toggle} busyId={busyId} />)}</div>
        </section>)}</div>
      </div>
      {!!roadmap.skills?.length && <aside className={styles.skillNavigation} aria-label="스킬 바로가기">
        <div className={styles.skillNavigationHeader}><h2>스킬 바로가기</h2><span>{roadmap.skills.length}</span></div>
        <nav>{roadmap.skills.map((skill) => {
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

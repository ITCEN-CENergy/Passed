import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import { generateRoadmap } from '../api/index.js'
import { BASKET_CHANGE_EVENT, clearRoadmapBasket, getRoadmapBasket, removeFromRoadmapBasket } from '../model/jobPostingBasket.js'
import styles from './JobPostingBasket.module.css'

const JobPostingBasket = () => {
  const navigate = useNavigate()
  const panelRef = useRef(null)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState(() => getRoadmapBasket())
  const [isGenerating, setIsGenerating] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const update = (event) => setItems(event.detail || getRoadmapBasket())
    window.addEventListener(BASKET_CHANGE_EVENT, update)
    window.addEventListener('storage', update)
    return () => { window.removeEventListener(BASKET_CHANGE_EVENT, update); window.removeEventListener('storage', update) }
  }, [])

  useEffect(() => {
    if (!open) return undefined
    const close = (event) => { if (!panelRef.current?.contains(event.target)) setOpen(false) }
    const escape = (event) => { if (event.key === 'Escape') setOpen(false) }
    document.addEventListener('pointerdown', close)
    document.addEventListener('keydown', escape)
    return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', escape) }
  }, [open])

  const generate = async () => {
    if (!items.length) return
    setIsGenerating(true); setError('')
    try {
      const result = await generateRoadmap(items.map(item => item.jobPostingId))
      clearRoadmapBasket()
      navigate(`/roadmap/${result.roadmapId}`)
    } catch (requestError) {
      setError(requestError.message || '로드맵을 생성하지 못했습니다.')
      setIsGenerating(false)
    }
  }

  return <>
    {isGenerating && createPortal(
      <PageLoading
        className={styles.pageLoading}
        title="학습 로드맵을 생성하고 있어요"
        description="선택한 공고의 요구 역량을 분석하고 있습니다."
        ariaLabel="학습 로드맵 생성 중"
      />,
      document.body,
    )}
    <div className={styles.wrapper} ref={panelRef}>
    {open && <section className={styles.panel} aria-label="학습 로드맵 공고함">
      <header><h2>학습 로드맵 공고함 <span>{items.length}</span></h2><button type="button" onClick={() => setOpen(false)} aria-label="공고함 닫기">×</button></header>
      <div className={styles.listHeader}><strong>선택한 공고 {items.length}개</strong><button type="button" disabled={!items.length} onClick={() => clearRoadmapBasket()}>전체 비우기</button></div>
      <div className={styles.list}>
        {items.length ? items.map(item => <article key={item.jobPostingId}><div><span>{item.companyName}</span><h3>{item.title}</h3></div><button type="button" onClick={() => removeFromRoadmapBasket(item.jobPostingId)}>삭제</button></article>) : <p className={styles.empty}>선택한 공고가 없어요.<br />로드맵으로 학습할 공고를 담아보세요.</p>}
      </div>
      <footer><p>선택한 공고의 요구 역량을 분석해<br />맞춤형 학습 계획을 생성합니다.</p>{error && <span className={styles.error} role="alert">{error}</span>}<button type="button" disabled={!items.length || isGenerating} onClick={generate}>{isGenerating ? '학습 로드맵 생성 중…' : '학습 로드맵 생성하기'}</button></footer>
    </section>}
    <button className={styles.trigger} type="button" aria-expanded={open} onClick={() => setOpen(value => !value)}><span className={styles.bag} aria-hidden="true" />공고함 <b>{items.length}</b></button>
    </div>
  </>
}
export default JobPostingBasket

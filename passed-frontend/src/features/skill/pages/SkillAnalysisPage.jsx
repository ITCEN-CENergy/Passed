import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { extractUserSkills, getSkillExtraction } from '../api/index.js'
import styles from './SkillAnalysisPage.module.css'

const stages = [
  '이력서 및 자기소개서 분석 중',
  '스킬 추출 중',
  '맞춤 역량 정리 중',
]

const stageIndexes = {
  DOCUMENT_ANALYSIS: 0,
  SKILL_EXTRACTION: 1,
  COMPETENCY_ORGANIZATION: 2,
}

const POLL_INTERVAL = 1500

const SkillAnalysisPage = () => {
  const navigate = useNavigate()
  const [stage, setStage] = useState(0)
  const [error, setError] = useState('')
  const [retryCount, setRetryCount] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let timer
    setStage(0); setError('')

    const handleError = (requestError) => {
      if (requestError.name !== 'AbortError') setError(requestError.message || '스킬을 분석하지 못했습니다.')
    }

    const poll = async (extractionId) => {
      const result = await getSkillExtraction(extractionId, { signal: controller.signal })
      if (controller.signal.aborted) return
      if (stageIndexes[result.stage] !== undefined) setStage(stageIndexes[result.stage])
      if (result.status === 'COMPLETED') {
        navigate('/onboarding/skills', { replace: true })
        return
      }
      if (result.status === 'FAILED') throw new Error(result.failureMessage || '스킬을 분석하지 못했습니다.')
      timer = window.setTimeout(() => poll(extractionId).catch(handleError), POLL_INTERVAL)
    }

    extractUserSkills({ signal: controller.signal })
      .then((run) => poll(run.extractionId))
      .catch(handleError)
    return () => { controller.abort(); window.clearTimeout(timer) }
  }, [navigate, retryCount])

  return (
    <main className={styles.page}>
      <section className={styles.loading} aria-live="polite" aria-busy={!error}>
        <span className={styles.dots} aria-hidden="true"><i /><i /><i /></span>
        {error ? (
          <><h1>분석을 완료하지 못했습니다</h1><p className={styles.error}>{error}</p><button type="button" onClick={() => setRetryCount((value) => value + 1)}>다시 시도</button></>
        ) : (
          <><h1>데이터를 분석하고 있어요</h1><p>{stages[stage]}</p><div className={styles.steps}>{stages.map((label, index) => <span className={index <= stage ? styles.active : ''} key={label}>{index < stage ? '✓' : index + 1}<b>{label}</b></span>)}</div></>
        )}
      </section>
    </main>
  )
}

export default SkillAnalysisPage

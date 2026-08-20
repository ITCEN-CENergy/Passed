import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageLoading } from '../../../common/components/index.js'
import { extractUserSkills, getSkillExtraction } from '../api/index.js'
import styles from './SkillAnalysisPage.module.css'

const stages = [
  '이력서 및 자기소개서 분석 중',
  '스킬 추출 중',
  '맞춤 역량 정리 중',
]

const POLL_INTERVAL = 1500
const STAGE_INTERVAL = 23000

const SkillAnalysisPage = () => {
  const navigate = useNavigate()
  const [stage, setStage] = useState(0)
  const [error, setError] = useState('')
  const [retryCount, setRetryCount] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let timer
    setStage(0); setError('')

    // 서버 단계는 일부 구간이 매우 짧으므로 안내 문구는 일정 간격으로 전환하고,
    // 실제 분석 완료 여부는 기존 폴링 결과로 판단합니다.
    const stageTimers = [
      window.setTimeout(() => setStage(1), STAGE_INTERVAL),
      window.setTimeout(() => setStage(2), STAGE_INTERVAL * 2),
    ]

    const handleError = (requestError) => {
      if (requestError.name !== 'AbortError') setError(requestError.message || '스킬을 분석하지 못했습니다.')
    }

    const poll = async (extractionId) => {
      const result = await getSkillExtraction(extractionId, { signal: controller.signal })
      if (controller.signal.aborted) return
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
    return () => {
      controller.abort()
      window.clearTimeout(timer)
      stageTimers.forEach((stageTimer) => window.clearTimeout(stageTimer))
    }
  }, [navigate, retryCount])

  return (
    <main className={styles.page}>
      {error ? (
        <section className={styles.errorState} role="alert">
          <h1>분석을 완료하지 못했습니다</h1>
          <p>{error}</p>
          <button type="button" onClick={() => setRetryCount((value) => value + 1)}>다시 시도</button>
        </section>
      ) : (
        <PageLoading
          title="데이터를 분석하고 있어요"
          description={stages[stage]}
          ariaLabel={`스킬 분석 중. ${stages[stage]}`}
          className={styles.analysisLoading}
        />
      )}
    </main>
  )
}

export default SkillAnalysisPage

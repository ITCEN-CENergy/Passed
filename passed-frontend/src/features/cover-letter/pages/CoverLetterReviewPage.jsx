// import { useState } from 'react'
// import { Link, useParams } from 'react-router-dom'
// import { useCoverLetterItemFeedback } from '../hooks'
// import styles from './styles/CoverLetterReviewPage.module.css'
//
// const IconCheck = ({ size = 16 }) => (
//   <svg
//     aria-hidden="true"
//     width={size}
//     height={size}
//     viewBox="0 0 24 24"
//     fill="none"
//     stroke="currentColor"
//     strokeWidth="2"
//     strokeLinecap="round"
//     strokeLinejoin="round"
//   >
//     <path d="M20 6 9 17l-5-5" />
//   </svg>
// )
//
// const IconCopy = ({ size = 16 }) => (
//   <svg
//     aria-hidden="true"
//     width={size}
//     height={size}
//     viewBox="0 0 24 24"
//     fill="none"
//     stroke="currentColor"
//     strokeWidth="2"
//     strokeLinecap="round"
//     strokeLinejoin="round"
//   >
//     <rect width="14" height="14" x="8" y="8" rx="2" />
//     <path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
//   </svg>
// )
//
// const IconRefresh = ({ size = 16 }) => (
//   <svg
//     aria-hidden="true"
//     width={size}
//     height={size}
//     viewBox="0 0 24 24"
//     fill="none"
//     stroke="currentColor"
//     strokeWidth="2"
//     strokeLinecap="round"
//     strokeLinejoin="round"
//   >
//     <path d="M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5" />
//     <path d="M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5" />
//   </svg>
// )
//
// function formatDate(value) {
//   if (!value) {
//     return ''
//   }
//
//   return new Intl.DateTimeFormat('ko-KR', {
//     dateStyle: 'medium',
//     timeStyle: 'short',
//   }).format(new Date(value))
// }
//
// function LoadingState() {
//   return (
//     <section className={styles.stateCard} aria-live="polite">
//       <div className={styles.spinner} />
//       <h1>자기소개서 문항을 불러오고 있어요</h1>
//       <p>더미 API의 응답을 기다리는 중입니다.</p>
//     </section>
//   )
// }
//
// function ErrorState({ error }) {
//   return (
//     <section className={`${styles.stateCard} ${styles.errorState}`} role="alert">
//       <span className={styles.stateBadge}>오류</span>
//       <h1>{error?.message ?? '화면을 불러오지 못했습니다.'}</h1>
//       <p className={styles.errorCode}>{error?.code ?? 'UNKNOWN_ERROR'}</p>
//       <Link className={styles.secondaryLink} to="/">
//         홈으로 돌아가기
//       </Link>
//     </section>
//   )
// }
//
// // Codex 참고: 현재 백엔드는 연결하지 않고 route의 문항 ID를 더미 API에 전달합니다.
// // 화면은 실제 연동을 고려해 조회/빈 결과/생성 중/완료/오류 상태를 모두 분리했습니다.
// export function CoverLetterReviewPage() {
//   const { companyCoverLetterItemId } = useParams()
//   const { item, feedback, isLoading, isGenerating, error, generateFeedback } =
//     useCoverLetterItemFeedback(companyCoverLetterItemId)
//   const [copied, setCopied] = useState(false)
//
//   async function copySuggestedAnswer() {
//     if (!feedback?.suggestedAnswer) {
//       return
//     }
//
//     await navigator.clipboard.writeText(feedback.suggestedAnswer)
//     setCopied(true)
//     window.setTimeout(() => setCopied(false), 1600)
//   }
//
//   if (isLoading) {
//     return <LoadingState />
//   }
//
//   if (!item) {
//     return <ErrorState error={error} />
//   }
//
//   return (
//     <div className={styles.pageShell}>
//       <div className={styles.demoNotice}>
//         <strong>프론트엔드 데모</strong>
//         <span>현재 화면은 백엔드 대신 인메모리 더미 데이터를 사용합니다.</span>
//       </div>
//
//       <header className={styles.pageHeader}>
//         <div>
//           <Link className={styles.backLink} to="/">
//             ← 홈
//           </Link>
//           <p className={styles.company}>
//             {item.companyName} · {item.jobPostingTitle}
//           </p>
//           <h1>{item.coverLetterTitle}</h1>
//           <p className={styles.itemMeta}>문항 {item.displayOrder}</p>
//         </div>
//
//         {feedback && (
//           <button
//             className={styles.secondaryButton}
//             disabled={isGenerating}
//             onClick={generateFeedback}
//             type="button"
//           >
//             <IconRefresh />
//             {isGenerating ? '재첨삭 중...' : '다시 첨삭'}
//           </button>
//         )}
//       </header>
//
//       {error && (
//         <div className={styles.inlineError} role="alert">
//           <strong>{error.message}</strong>
//           <span>{error.code}</span>
//         </div>
//       )}
//
//       <main className={styles.content} aria-busy={isGenerating}>
//         <section className={styles.card}>
//           <div className={styles.cardHeading}>
//             <div>
//               <span className={styles.sectionEyebrow}>자기소개서 문항</span>
//               <h2>{item.questionText}</h2>
//             </div>
//             <span className={styles.characterCount}>
//               {item.answer.length}
//               {item.characterLimit ? ` / ${item.characterLimit}자` : '자'}
//             </span>
//           </div>
//           <p className={styles.originalAnswer}>{item.answer}</p>
//         </section>
//
//         {!feedback ? (
//           <section className={`${styles.card} ${styles.emptyFeedback}`}>
//             <span className={styles.emptyIcon}>AI</span>
//             <h2>아직 생성된 문항별 첨삭이 없어요</h2>
//             <p>
//               질문 의도와 채용공고 적합도를 확인하고, 개선된 답변을 더미 데이터로
//               생성합니다.
//             </p>
//             <button
//               className={styles.primaryButton}
//               disabled={isGenerating}
//               onClick={generateFeedback}
//               type="button"
//             >
//               {isGenerating ? (
//                 <>
//                   <span className={styles.buttonSpinner} />
//                   첨삭 생성 중...
//                 </>
//               ) : (
//                 '문항 첨삭 시작'
//               )}
//             </button>
//           </section>
//         ) : (
//           <>
//             <section className={styles.feedbackSummary}>
//               <div
//                 className={`${styles.scoreCard} ${
//                   styles[`score${feedback.score}`] ?? ''
//                 }`}
//               >
//                 <span className={styles.scoreCaption}>문항 평가</span>
//                 <strong>{feedback.scoreLabel}</strong>
//                 <span>질문과 답변의 연결을 기준으로 평가했어요.</span>
//               </div>
//
//               <div className={styles.limitCard}>
//                 <span className={styles.scoreCaption}>수정안 글자 수</span>
//                 <strong>
//                   {feedback.suggestedAnswerLength}
//                   {feedback.characterLimit ? ` / ${feedback.characterLimit}자` : '자'}
//                 </strong>
//                 <span
//                   className={
//                     feedback.withinCharacterLimit ? styles.limitGood : styles.limitWarning
//                   }
//                 >
//                   {feedback.withinCharacterLimit
//                     ? '글자 수 제한을 충족합니다.'
//                     : '글자 수 제한을 초과했습니다.'}
//                 </span>
//               </div>
//             </section>
//
//             {feedback.strengths && (
//               <section className={styles.card}>
//                 <div className={styles.cardTitleRow}>
//                   <span className={`${styles.iconCircle} ${styles.goodIcon}`}>
//                     <IconCheck />
//                   </span>
//                   <h2>잘된 점</h2>
//                 </div>
//                 <p className={styles.feedbackText}>{feedback.strengths}</p>
//               </section>
//             )}
//
//             <section className={styles.card}>
//               <div className={styles.cardTitleRow}>
//                 <span className={`${styles.iconCircle} ${styles.improveIcon}`}>!</span>
//                 <h2>개선할 점</h2>
//               </div>
//               <p className={`${styles.feedbackText} ${styles.preserveLines}`}>
//                 {feedback.improvements}
//               </p>
//             </section>
//
//             <section className={`${styles.card} ${styles.suggestionCard}`}>
//               <div className={styles.cardHeading}>
//                 <div>
//                   <span className={styles.sectionEyebrow}>AI 수정 제안</span>
//                   <h2>이렇게 다듬어 보세요</h2>
//                 </div>
//                 <button
//                   className={styles.copyButton}
//                   onClick={copySuggestedAnswer}
//                   type="button"
//                 >
//                   {copied ? <IconCheck /> : <IconCopy />}
//                   {copied ? '복사됨' : '수정안 복사'}
//                 </button>
//               </div>
//               <p className={styles.suggestedAnswer}>{feedback.suggestedAnswer}</p>
//               <p className={styles.updatedAt}>
//                 최근 첨삭 {formatDate(feedback.updatedAt)}
//               </p>
//             </section>
//           </>
//         )}
//       </main>
//
//       {isGenerating && feedback && (
//         <div className={styles.generatingOverlay} aria-live="polite">
//           <span className={styles.buttonSpinner} />
//           새로운 첨삭 결과를 만들고 있어요.
//         </div>
//       )}
//     </div>
//   )
// }
//
// export default CoverLetterReviewPage

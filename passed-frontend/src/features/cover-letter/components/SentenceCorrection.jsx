import React from 'react';
import styles from './SentenceCorrection.module.css';

const SentenceCorrection = () => {
  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <h2 className={styles.title}>문장별 첨삭</h2>
        <div className={styles.navigation}>
          <span className={styles.pageText}>
            <span className={styles.pageCurrent}>1</span> / 8문장
          </span>
          <div className={styles.navButtons}>
            <button className={styles.navBtn}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="15 18 9 12 15 6"></polyline>
              </svg>
            </button>
            <button className={styles.navBtn}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="9 18 15 12 9 6"></polyline>
              </svg>
            </button>
          </div>
        </div>
      </div>

      <div className={styles.activeSentence}>
        <div className={styles.comparison}>
          <div className={styles.textColumn}>
            <div className={styles.columnHeader}>원문</div>
            <p className={styles.textContent}>
              저는 <span className={styles.highlightRed}>다양한 프로젝트를</span> 통해 백엔드 개발 역량을 <span className={styles.highlightRed}>확인합니다</span>.
            </p>
            
            <div className={styles.issueBox}>
              <div className={styles.issueHeader}>
                <span className={styles.issueLabel}>이슈</span>
                <span className={styles.issueBadge}>추상적 표현</span>
              </div>
              <div className={styles.issueReason}>
                <span className={styles.reasonLabel}>수정 이유</span>
                <p className={styles.reasonText}>구체적인 경험과 성과를 명시하면 지원자의 역량을 더 효과적으로 전달할 수 있어요.</p>
              </div>
            </div>
          </div>

          <div className={styles.arrowIcon}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#D3D3D3" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="5" y1="12" x2="19" y2="12"></line>
              <polyline points="12 5 19 12 12 19"></polyline>
            </svg>
          </div>

          <div className={styles.textColumn}>
            <div className={styles.columnHeader}>
              수정문
              <span className={styles.reflectedBadge}>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                  <polyline points="22 4 12 14.01 9 11.01"></polyline>
                </svg>
                반영됨
              </span>
            </div>
            <p className={styles.textContent}>
              저는 <span className={styles.highlightBlue}>대용량 트래픽을 처리하는</span> 백엔드 시스템을 설계하고 개발한 <span className={styles.highlightBlue}>3가지 프로젝트를</span> 통해 역량을 키워왔습니다.
            </p>
          </div>
        </div>

        <div className={styles.actions}>
          <button className={`${styles.btn} ${styles.btnPrimary}`}>수정안 반영</button>
          <button className={`${styles.btn} ${styles.btnSecondary}`}>원문 유지</button>
          <button className={`${styles.btn} ${styles.btnSecondary}`}>직접 편집</button>
        </div>
      </div>

      <div className={styles.accordionList}>
        <div className={styles.accordionItem}>
          <span className={styles.accordionTitle}>2. 문장 검토 중</span>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#666" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="6 9 12 15 18 9"></polyline>
          </svg>
        </div>
        <div className={styles.accordionItem}>
          <span className={styles.accordionTitle}>3. 문장 검토 대기</span>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#666" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="6 9 12 15 18 9"></polyline>
          </svg>
        </div>
      </div>
    </div>
  );
};

export default SentenceCorrection;

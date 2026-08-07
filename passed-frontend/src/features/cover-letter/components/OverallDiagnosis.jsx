import React from 'react';
import styles from './OverallDiagnosis.module.css';

const OverallDiagnosis = () => {
  return (
    <div className={styles.card}>
      <h2 className={styles.title}>종합 진단</h2>
      <p className={styles.summary}>회사 이해도는 좋지만, 경험과 성과를 더 구체적으로 보여주세요.</p>
      
      <div className={styles.details}>
        <div className={styles.detailSection}>
          <div className={styles.detailHeader}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#00C851" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
              <polyline points="22 4 12 14.01 9 11.01"></polyline>
            </svg>
            <span className={styles.goodPoint}>잘된 점</span>
          </div>
          <p className={styles.detailText}>카카오 서비스와 기술 스택에 대한 이해가 잘 드러나요.</p>
        </div>
        
        <div className={styles.divider}></div>
        
        <div className={styles.detailSection}>
          <div className={styles.detailHeader}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#FF9800" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
              <line x1="12" y1="9" x2="12" y2="13"></line>
              <line x1="12" y1="17" x2="12.01" y2="17"></line>
            </svg>
            <span className={styles.improvementPoint}>우선 개선할 점</span>
          </div>
          <p className={styles.detailText}>경험과 성과를 수치와 사례로 더 구체화해 주세요.</p>
        </div>
      </div>
    </div>
  );
};

export default OverallDiagnosis;

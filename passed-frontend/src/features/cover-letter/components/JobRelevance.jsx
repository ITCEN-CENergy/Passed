import React from 'react';
import styles from './JobRelevance.module.css';

const JobRelevance = () => {
  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <h2 className={styles.title}>공고 연관성</h2>
        <div className={styles.stats}>
          <div className={styles.statItem}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#00C851" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
              <polyline points="22 4 12 14.01 9 11.01"></polyline>
            </svg>
            <span>반영 2</span>
          </div>
          <div className={styles.statItem}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#FF9800" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10"></circle>
              <path d="M12 2a10 10 0 0 1 10 10"></path>
            </svg>
            <span>부분 반영 2</span>
          </div>
          <div className={styles.statItem}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#999" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10"></circle>
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path>
              <line x1="12" y1="17" x2="12.01" y2="17"></line>
            </svg>
            <span>확인 필요 1</span>
          </div>
        </div>
      </div>

      <div className={styles.list}>
        <div className={styles.listItem}>
          <div className={styles.itemLeft}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#00C851" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
              <polyline points="22 4 12 14.01 9 11.01"></polyline>
            </svg>
            <span className={styles.itemTitle}>대용량 트래픽 처리</span>
          </div>
          <div className={styles.itemRight}>
            <span className={styles.statusReflected}>반영</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#666" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </div>
        </div>

        <div className={styles.listItem}>
          <div className={styles.itemLeft}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#FF9800" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10"></circle>
              <path d="M12 2a10 10 0 0 1 10 10"></path>
            </svg>
            <span className={styles.itemTitle}>Spring 기반 개발</span>
          </div>
          <div className={styles.itemRight}>
            <span className={styles.statusPartial}>부분 반영</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#666" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </div>
        </div>

        <div className={styles.listItem}>
          <div className={styles.itemLeft}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#999" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10"></circle>
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path>
              <line x1="12" y1="17" x2="12.01" y2="17"></line>
            </svg>
            <span className={styles.itemTitle}>카카오 서비스 이해</span>
          </div>
          <div className={styles.itemRight}>
            <span className={styles.statusNeedCheck}>확인 필요</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#666" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </div>
        </div>
      </div>
    </div>
  );
};

export default JobRelevance;

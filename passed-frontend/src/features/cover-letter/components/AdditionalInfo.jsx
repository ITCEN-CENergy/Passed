import React from 'react';
import styles from './AdditionalInfo.module.css';

const AdditionalInfo = () => {
  return (
    <div className={styles.card}>
      <h2 className={styles.title}>추가 정보가 필요해요</h2>
      <p className={styles.prompt}>프로젝트에서 본인이 맡은 역할을 알려주세요.</p>
      
      <div className={styles.inputWrapper}>
        <textarea 
          className={styles.textarea} 
          placeholder="답변을 입력하세요."
          maxLength={500}
        ></textarea>
        <span className={styles.charCount}>0/500</span>
      </div>

      <div className={styles.disclaimer}>
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
          <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
        </svg>
        <span>확인되지 않은 사실은 임의로 작성하지 않아요.</span>
      </div>
    </div>
  );
};

export default AdditionalInfo;

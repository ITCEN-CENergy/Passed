import React from 'react';
import styles from './Footer.module.css';

const Footer = () => {
  return (
    <footer className={styles.footer}>
      <div className={styles.container}>
        <div className={styles.charCount}>
          <span className={styles.currentCount}>672</span> / 700자
        </div>
        <div className={styles.actions}>
          <button className={`${styles.btn} ${styles.btnSecondary}`}>재첨삭</button>
          <button className={`${styles.btn} ${styles.btnPrimary}`}>전체 수정안 보기</button>
        </div>
      </div>
    </footer>
  );
};

export default Footer;

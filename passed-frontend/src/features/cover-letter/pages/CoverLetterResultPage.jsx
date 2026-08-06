import React from 'react';
import Header from '../components/Header';
import OverallDiagnosis from '../components/OverallDiagnosis';
import JobRelevance from '../components/JobRelevance';
import SentenceCorrection from '../components/SentenceCorrection';
import AdditionalInfo from '../components/AdditionalInfo';
import Footer from '../components/Footer';
import styles from './CoverLetterResultPage.module.css';

const CoverLetterResultPage = () => {
  return (
    <div className={styles.container}>
      <Header />
      <main className={styles.mainContent}>
        <OverallDiagnosis />
        <JobRelevance />
        <SentenceCorrection />
        <AdditionalInfo />
      </main>
      <Footer />
    </div>
  );
};

export default CoverLetterResultPage;

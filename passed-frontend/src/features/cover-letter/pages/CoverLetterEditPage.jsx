import './CoverLetterEditPage.css';

const IconCheckCircle2 = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z"/>
    <path d="m9 12 2 2 4-4"/>
  </svg>
);

const IconRotateCcw = ({ size = 16 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
    <path d="M3 3v5h5"/>
  </svg>
);

const IconMoreVertical = ({ size = 20 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="1"/><circle cx="12" cy="5" r="1"/><circle cx="12" cy="19" r="1"/>
  </svg>
);

const IconHelpCircle = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><path d="M12 17h.01"/>
  </svg>
);

const IconChevronLeft = ({ size = 14 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="m15 18-6-6 6-6"/>
  </svg>
);

const IconChevronRight = ({ size = 14 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="m9 18 6-6-6-6"/>
  </svg>
);

const IconChevronDown = ({ size = 16, color }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{color}}>
    <path d="m6 9 6 6 6-6"/>
  </svg>
);

const IconLock = ({ size = 12 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
  </svg>
);

export function CoverLetterEditPage() {
  return (
    <div className="app-container">
      {/* Header */}
      <header className="header">
        <div className="header-left">
          <div className="logo">PASSED</div>
          <div className="header-title">카카오 • 백엔드 개발자</div>
        </div>
        <div className="header-right">
          <button className="btn-outline">
            <IconRotateCcw size={16} />
            다시 분석
          </button>
          <div className="status-saved">
            저장됨 <IconCheckCircle2 size={16} />
          </div>
          <button className="icon-button">
            <IconMoreVertical size={20} />
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="main-content">
        
        {/* Comprehensive Diagnosis */}
        <section className="section-card">
          <h2 className="section-title">종합 진단</h2>
          <div className="diagnosis-summary">
            회사 이해도는 좋지만, 경험과 성과를 더 구체적으로 보여주세요.
          </div>
          <div className="diagnosis-grid">
            <div className="diagnosis-item">
              <div className="diagnosis-item-header good">
                <IconCheckCircle2 size={16} /> 잘된 점
              </div>
              <div className="diagnosis-item-desc">
                카카오 서비스와 기술 스택에 대해 이해가 잘 드러나요.
              </div>
            </div>
            <div className="diagnosis-item">
              <div className="diagnosis-item-header improve">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg>
                우선 개선할 점
              </div>
              <div className="diagnosis-item-desc">
                경험과 성과를 수치와 사례로 더 구체화해 주세요.
              </div>
            </div>
          </div>
        </section>

        {/* Job Relevance */}
        <section className="section-card">
          <h2 className="section-title">
            공고 연관성
            <div className="relevance-header-stats">
              <span className="stat-item reflected"><IconCheckCircle2 size={14} /> 반영 2</span>
              <span className="stat-item partial">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z"/><path d="m9 12 2 2 4-4"/></svg> 
                부분 반영 2
              </span>
              <span className="stat-item needs-check"><IconHelpCircle size={14} /> 확인 필요 1</span>
            </div>
          </h2>
          
          <div className="relevance-list">
            <div className="relevance-list-item">
              <div className="relevance-item-left">
                <IconCheckCircle2 size={16} className="reflected" /> 대용량 트래픽 처리
              </div>
              <div className="relevance-item-right reflected">
                반영 <IconChevronDown size={16} color="var(--text-muted)" />
              </div>
            </div>
            <div className="relevance-list-item">
              <div className="relevance-item-left">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="partial"><path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z"/><path d="m9 12 2 2 4-4"/></svg>
                Spring 기반 개발
              </div>
              <div className="relevance-item-right partial">
                부분 반영 <IconChevronDown size={16} color="var(--text-muted)" />
              </div>
            </div>
            <div className="relevance-list-item">
              <div className="relevance-item-left">
                <IconHelpCircle size={16} className="needs-check" /> 카카오 서비스 이해
              </div>
              <div className="relevance-item-right needs-check">
                확인 필요 <IconChevronDown size={16} color="var(--text-muted)" />
              </div>
            </div>
          </div>
        </section>

        {/* Sentence Editing */}
        <section className="section-card">
          <h2 className="section-title">
            문장별 첨삭
            <div className="pager">
              1 / 8문장
              <button className="pager-btn"><IconChevronLeft size={14} /></button>
              <button className="pager-btn"><IconChevronRight size={14} /></button>
            </div>
          </h2>

          <div className="edit-comparison">
            <div className="edit-box">
              <div className="edit-box-title">원문</div>
              저는 <span className="highlight-red">다양한 프로젝트를</span> 통해 백엔드 개발 역량을 키웠습니다
            </div>
            <div className="edit-arrow">→</div>
            <div className="edit-box">
              <div className="edit-box-title">수정문</div>
              <div className="edit-badge"><IconCheckCircle2 size={12} /> 반영됨</div>
              <br/>
              저는 대용량 트래픽을 처리하는 백엔드 시스템을 설계하고 개발한 <span className="highlight-blue">3가지 프로젝트를</span> 통해 역량을 키웠습니다
            </div>
          </div>

          <div className="issue-box">
            <div className="issue-header">
              이슈 <span className="issue-badge">추상적 표현</span>
            </div>
            <div className="edit-box-title">수정 이유</div>
            <div className="issue-desc">
              구체적인 경험과 성과를 명시하면 지원자의 역량을 더 효과적으로 전달할 수 있어요.
            </div>
            <div className="issue-actions">
              <button className="btn-primary">수정안 반영</button>
              <button className="btn-secondary">원문 유지</button>
              <button className="btn-secondary">직접 편집</button>
            </div>
          </div>

          <div className="collapsible-item">
            2. 문장 검토 중 <IconChevronDown size={16} color="var(--text-normal)" />
          </div>
          <div className="collapsible-item" style={{ borderBottom: 'none' }}>
            3. 문장 검토 대기 <IconChevronDown size={16} color="var(--text-normal)" />
          </div>
        </section>

        {/* Additional Info Needed */}
        <section className="section-card">
          <h2 className="section-title" style={{ fontSize: '1.125rem' }}>추가 정보가 필요해요</h2>
          <div className="additional-info-desc">
            프로젝트에서 본인이 맡은 역할을 알려주세요.
          </div>
          <div className="textarea-container">
            <textarea placeholder="답변을 입력하세요."></textarea>
            <div className="char-count">0/500</div>
          </div>
          <div className="info-notice">
            <IconLock size={12} /> 확인되지 않은 사실은 임의로 작성하지 않아요.
          </div>
        </section>

      </main>

      {/* Bottom Bar */}
      <div className="bottom-bar">
        <div className="bottom-bar-content">
          <div className="total-char-count">
            672 <span>/ 700자</span>
          </div>
          <div className="bottom-actions">
            <button className="btn-outline btn-large">재첨삭</button>
            <button className="btn-primary btn-large">전체 수정안 보기</button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default CoverLetterEditPage;

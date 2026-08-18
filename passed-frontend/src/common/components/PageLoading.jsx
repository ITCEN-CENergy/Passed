import styles from './PageLoading.module.css'

/**
 * 페이지 콘텐츠 영역에 표시하는 공통 로딩 컴포넌트입니다.
 * fullPage를 사용하면 헤더를 제외한 화면 전체의 중앙에 표시합니다.
 * 그 외에는 페이지가 필요한 높이를 className으로 지정할 수 있습니다.
 */
const PageLoading = ({
  title = '데이터를 불러오고 있어요',
  description = '잠시만 기다려주세요.',
  ariaLabel,
  className = '',
  fullPage = false,
}) => (
  <div
    className={`${styles.loading} ${fullPage ? styles.fullPage : ''} ${className}`.trim()}
    role="status"
    aria-label={ariaLabel || `${title}. ${description}`}
    aria-live="polite"
  >
    <span className={styles.dots} aria-hidden="true">
      <i /><i /><i />
    </span>
    <strong>{title}</strong>
    {description && <p>{description}</p>}
  </div>
)

export default PageLoading

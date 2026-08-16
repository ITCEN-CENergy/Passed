import { PageLoading } from '../../../common/components/index.js'
import styles from './LoadingOverlay.module.css'

const LoadingOverlay = ({ description = '잠시만 기다려 주세요.', title }) => (
  <div className={styles.backdrop} role="dialog" aria-modal="true" aria-label={title}>
    <div className={styles.dialog}>
      <PageLoading
        className={styles.content}
        title={title}
        description={description}
        ariaLabel={`${title}. ${description}`}
      />
    </div>
  </div>
)

export default LoadingOverlay

import styles from './PageState.module.css'

const PageState = ({ title, description, action, loading = false }) => (
  <div className={styles.state} role={loading ? 'status' : 'alert'} aria-live="polite">
    {loading && <span className={styles.spinner} aria-hidden="true" />}
    <strong>{title}</strong>
    {description && <p>{description}</p>}
    {action}
  </div>
)

export default PageState

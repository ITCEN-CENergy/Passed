import { EyeIcon } from './AuthIcons.jsx'
import styles from './AuthForm.module.css'

const AuthField = ({
  id,
  label,
  icon,
  error,
  type = 'text',
  showPassword,
  onTogglePassword,
  ...inputProps
}) => {
  const isPassword = type === 'password'
  const inputType = isPassword && showPassword ? 'text' : type
  const describedBy = error ? `${id}-error` : undefined

  return (
    <div className={styles.fieldGroup}>
      <label className={styles.label} htmlFor={id}>{label}</label>
      <div className={`${styles.inputFrame} ${error ? styles.inputError : ''}`}>
        <span className={styles.leadingIcon}>{icon}</span>
        <input
          {...inputProps}
          id={id}
          type={inputType}
          aria-invalid={Boolean(error)}
          aria-describedby={describedBy}
        />
        {isPassword && (
          <button
            className={styles.passwordToggle}
            type="button"
            onClick={onTogglePassword}
            aria-label={showPassword ? `${label} 숨기기` : `${label} 보기`}
          >
            <EyeIcon hidden={showPassword} />
          </button>
        )}
      </div>
      {error && <p className={styles.fieldError} id={describedBy}>{error}</p>}
    </div>
  )
}

export default AuthField

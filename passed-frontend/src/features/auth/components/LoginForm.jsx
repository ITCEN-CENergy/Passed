import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { login } from '../api/authApi.js'
import useAuthStore from '../model/useAuthStore.js'
import AuthField from './AuthField.jsx'
import { LockIcon, MailIcon, ShieldIcon } from './AuthIcons.jsx'
import styles from './AuthForm.module.css'

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const LoginForm = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const refreshUser = useAuthStore((state) => state.refreshUser)
  const [form, setForm] = useState({ email: '', password: '' })
  const [errors, setErrors] = useState({})
  const [formMessage, setFormMessage] = useState(location.state?.message || '')
  const [messageType, setMessageType] = useState(location.state?.message ? 'success' : 'error')
  const [showPassword, setShowPassword] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const updateField = (event) => {
    const { name, value, checked, type } = event.target
    setForm((current) => ({
      ...current,
      [name]: type === 'checkbox' ? checked : value,
    }))
    setErrors((current) => ({ ...current, [name]: '' }))
    setFormMessage('')
  }

  const validate = () => {
    const nextErrors = {}
    if (!form.email.trim()) nextErrors.email = '이메일을 입력해주세요.'
    else if (!emailPattern.test(form.email.trim())) nextErrors.email = '올바른 이메일 형식을 입력해주세요.'
    if (!form.password) nextErrors.password = '비밀번호를 입력해주세요.'
    return nextErrors
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    const nextErrors = validate()
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors)
      return
    }

    setIsSubmitting(true)
    setFormMessage('')
    try {
      await login({ email: form.email.trim(), password: form.password })
      await refreshUser()
      const returnTo = location.state?.returnTo
      navigate(typeof returnTo === 'string' && returnTo.startsWith('/') ? returnTo : '/')
    } catch (error) {
      setMessageType('error')
      setFormMessage(error.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className={styles.page} aria-labelledby="login-title">
      <div className={styles.card}>
        <div className={styles.heading}>
          <h1 id="login-title">로그인</h1>
          <p>PASSED에서 나만의 취업 준비를 이어가세요</p>
        </div>

        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          <AuthField
            id="login-email"
            name="email"
            label="이메일"
            type="email"
            inputMode="email"
            autoComplete="email"
            placeholder="example@email.com"
            value={form.email}
            onChange={updateField}
            icon={<MailIcon />}
            error={errors.email}
          />
          <AuthField
            id="login-password"
            name="password"
            label="비밀번호"
            type="password"
            autoComplete="current-password"
            placeholder="비밀번호를 입력해주세요"
            value={form.password}
            onChange={updateField}
            icon={<LockIcon />}
            error={errors.password}
            showPassword={showPassword}
            onTogglePassword={() => setShowPassword((visible) => !visible)}
          />

          {formMessage && (
            <p
              className={`${styles.formMessage} ${messageType === 'success' ? styles.successMessage : ''}`}
              role="status"
              aria-live="polite"
            >
              {formMessage}
            </p>
          )}

          <button className={styles.submitButton} type="submit" disabled={isSubmitting}>
            {isSubmitting ? '로그인 중...' : '로그인'}
          </button>
          <p className={styles.switchPrompt}>
            아직 계정이 없으신가요?
            <Link className={styles.inlineLink} to="/signup">회원가입</Link>
          </p>
        </form>
      </div>
      <p className={styles.securityNotice}>
        <ShieldIcon />
        안전하게 보호되는 로그인 환경입니다
      </p>
    </section>
  )
}

export default LoginForm

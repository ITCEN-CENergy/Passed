import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { checkEmail, signup } from '../api/authApi.js'
import AuthField from './AuthField.jsx'
import { LockIcon, MailIcon, UserIcon } from './AuthIcons.jsx'
import styles from './AuthForm.module.css'

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/

const SignupForm = () => {
  const navigate = useNavigate()
  const [form, setForm] = useState({
    name: '',
    email: '',
    password: '',
    passwordConfirm: '',
    requiredAgreement: false,
    marketingAgreement: false,
  })
  const [errors, setErrors] = useState({})
  const [formMessage, setFormMessage] = useState('')
  const [emailStatus, setEmailStatus] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const updateField = (event) => {
    const { name, value, checked, type } = event.target
    setForm((current) => ({
      ...current,
      [name]: type === 'checkbox' ? checked : value,
    }))
    setErrors((current) => ({ ...current, [name]: '' }))
    setFormMessage('')
    if (name === 'email') setEmailStatus('')
  }

  const validate = () => {
    const nextErrors = {}
    const trimmedName = form.name.trim()
    const trimmedEmail = form.email.trim()

    if (!trimmedName) nextErrors.name = '이름을 입력해주세요.'
    else if (trimmedName.length > 100) nextErrors.name = '이름은 100자 이하로 입력해주세요.'
    if (!trimmedEmail) nextErrors.email = '이메일을 입력해주세요.'
    else if (!emailPattern.test(trimmedEmail)) nextErrors.email = '올바른 이메일 형식을 입력해주세요.'
    if (!form.password) nextErrors.password = '비밀번호를 입력해주세요.'
    else if (!passwordPattern.test(form.password)) {
      nextErrors.password = '영문과 숫자를 포함해 8~72자로 입력해주세요.'
    }
    if (!form.passwordConfirm) nextErrors.passwordConfirm = '비밀번호를 다시 입력해주세요.'
    else if (form.password !== form.passwordConfirm) {
      nextErrors.passwordConfirm = '비밀번호가 일치하지 않습니다.'
    }
    if (!form.requiredAgreement) nextErrors.requiredAgreement = '필수 약관에 동의해주세요.'
    return nextErrors
  }

  const handleEmailBlur = async () => {
    const email = form.email.trim()
    if (!emailPattern.test(email)) return

    setEmailStatus('checking')
    try {
      const duplicated = await checkEmail(email)
      if (duplicated) {
        setErrors((current) => ({ ...current, email: '이미 사용 중인 이메일입니다.' }))
        setEmailStatus('duplicated')
      } else {
        setEmailStatus('available')
      }
    } catch {
      setEmailStatus('')
    }
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    const nextErrors = validate()
    if (emailStatus === 'duplicated') nextErrors.email = '이미 사용 중인 이메일입니다.'
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors)
      return
    }

    setIsSubmitting(true)
    setFormMessage('')
    try {
      await signup({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
      })
      navigate('/login', {
        replace: true,
        state: { message: '회원가입이 완료되었습니다. 로그인해주세요.' },
      })
    } catch (error) {
      setFormMessage(error.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className={styles.page} aria-labelledby="signup-title">
      <div className={`${styles.card} ${styles.signupCard}`}>
        <div className={styles.heading}>
          <h1 id="signup-title">회원가입</h1>
          <p>PASSED와 함께 맞춤형 취업 준비를 시작해보세요</p>
        </div>

        <form className={`${styles.form} ${styles.signupForm}`} onSubmit={handleSubmit} noValidate>
          <AuthField
            id="signup-name"
            name="name"
            label="이름"
            autoComplete="name"
            maxLength={100}
            placeholder="이름을 입력해주세요"
            value={form.name}
            onChange={updateField}
            icon={<UserIcon />}
            error={errors.name}
          />
          <div>
            <AuthField
              id="signup-email"
              name="email"
              label="이메일"
              type="email"
              inputMode="email"
              autoComplete="email"
              placeholder="example@email.com"
              value={form.email}
              onChange={updateField}
              onBlur={handleEmailBlur}
              icon={<MailIcon />}
              error={errors.email}
            />
            {emailStatus === 'checking' && <p className={styles.fieldSuccess}>이메일을 확인하고 있습니다.</p>}
            {emailStatus === 'available' && !errors.email && (
              <p className={styles.fieldSuccess}>사용 가능한 이메일입니다.</p>
            )}
          </div>
          <AuthField
            id="signup-password"
            name="password"
            label="비밀번호"
            type="password"
            autoComplete="new-password"
            placeholder="영문, 숫자 포함 8자 이상"
            value={form.password}
            onChange={updateField}
            icon={<LockIcon />}
            error={errors.password}
            showPassword={showPassword}
            onTogglePassword={() => setShowPassword((visible) => !visible)}
          />
          <AuthField
            id="signup-password-confirm"
            name="passwordConfirm"
            label="비밀번호 확인"
            type="password"
            autoComplete="new-password"
            placeholder="비밀번호를 다시 입력해주세요"
            value={form.passwordConfirm}
            onChange={updateField}
            icon={<LockIcon />}
            error={errors.passwordConfirm}
            showPassword={showPasswordConfirm}
            onTogglePassword={() => setShowPasswordConfirm((visible) => !visible)}
          />

          <div className={styles.agreementBox}>
            <label className={styles.checkboxRow}>
              <input
                type="checkbox"
                name="requiredAgreement"
                checked={form.requiredAgreement}
                onChange={updateField}
              />
              <span>
                [필수] <strong>이용약관</strong> 및 <strong>개인정보 처리방침</strong>에 동의합니다
              </span>
            </label>
            <label className={styles.checkboxRow}>
              <input
                type="checkbox"
                name="marketingAgreement"
                checked={form.marketingAgreement}
                onChange={updateField}
              />
              <span>[선택] 취업 정보 및 학습 알림 수신에 동의합니다</span>
            </label>
            {errors.requiredAgreement && (
              <p className={styles.agreementError} role="alert">{errors.requiredAgreement}</p>
            )}
          </div>

          {formMessage && (
            <p className={styles.formMessage} role="alert" aria-live="assertive">{formMessage}</p>
          )}

          <button className={styles.submitButton} type="submit" disabled={isSubmitting}>
            {isSubmitting ? '가입 중...' : '회원가입'}
          </button>
          <p className={styles.switchPrompt}>
            이미 계정이 있으신가요?
            <Link className={styles.inlineLink} to="/login">로그인</Link>
          </p>
        </form>
      </div>
    </section>
  )
}

export default SignupForm

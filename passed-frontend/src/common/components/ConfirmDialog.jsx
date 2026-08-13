import { useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'
import styles from './ConfirmDialog.module.css'

/**
 * 팀 공통 확인 다이얼로그입니다. open이 true일 때만 DOM에 렌더링됩니다.
 * 버튼의 비동기 처리 중에는 isLoading을 전달하세요.
 */
const ConfirmDialog = ({
  open,
  title = '변경사항을 적용할까요?',
  description = '적용 후에는 이전 상태로 되돌릴 수 없습니다.',
  cancelText = '취소',
  confirmText = '적용하기',
  loadingText = '처리 중…',
  closeLabel = '다이얼로그 닫기',
  iconLabel = '안내',
  onCancel,
  onConfirm,
  isLoading = false,
  closeOnBackdrop = true,
  showCloseButton = true,
  children,
}) => {
  const titleId = useId()
  const descriptionId = useId()
  const cancelButtonRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    cancelButtonRef.current?.focus()
    const onKeyDown = (event) => {
      if (event.key === 'Escape' && !isLoading) onCancel?.()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [isLoading, onCancel, open])

  if (!open) return null

  return createPortal(
    <div
      className={styles.backdrop}
      onMouseDown={(event) => {
        if (closeOnBackdrop && !isLoading && event.target === event.currentTarget) onCancel?.()
      }}
    >
      <section
        className={styles.dialog}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
      >
        {showCloseButton && <button className={styles.close} type="button" aria-label={closeLabel} disabled={isLoading} onClick={onCancel}>×</button>}
        <span className={styles.icon} role="img" aria-label={iconLabel}>i</span>
        <h2 id={titleId}>{title}</h2>
        {description && <p id={descriptionId}>{description}</p>}
        {children && <div className={styles.content}>{children}</div>}
        <div className={styles.actions}>
          <button ref={cancelButtonRef} className={styles.cancel} type="button" disabled={isLoading} onClick={onCancel}>{cancelText}</button>
          <button className={styles.confirm} type="button" disabled={isLoading} onClick={onConfirm}>{isLoading ? loadingText : confirmText}</button>
        </div>
      </section>
    </div>,
    document.body,
  )
}

export default ConfirmDialog

import styles from './Footer.module.css'

const Footer = () => (
  <footer className={styles.footer}>
    <div className={styles.inner}>
      <small>© Passed Inc. All rights reserved.</small>
      <nav aria-label="정책 메뉴">
        <a href="#inquiry">문의</a>
        <a href="#terms">이용약관</a>
        <a href="#privacy">개인정보처리방침</a>
      </nav>
    </div>
  </footer>
)

export default Footer

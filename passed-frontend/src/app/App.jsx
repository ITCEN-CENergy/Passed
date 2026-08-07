import { NavLink, Outlet, useLocation } from 'react-router-dom'
import './App.css'

function App() {
  const { pathname } = useLocation()
  const isCoverLetterList = pathname === '/cover-letter-list'

  return (
    <div className="app">
      {!isCoverLetterList && (
      <header className="header">
        <NavLink className="logo" to="/">
          Passed
        </NavLink>

        <nav aria-label="주요 메뉴">
          <NavLink
            className={({ isActive }) => (isActive ? 'active' : undefined)}
            end
            to="/"
          >
            홈
          </NavLink>
          <NavLink
            className={({ isActive }) => (isActive ? 'active' : undefined)}
            to="/about"
          >
            소개
          </NavLink>
        </nav>
      </header>
      )}

      <main>
        <Outlet />
      </main>
    </div>
  )
}

export default App

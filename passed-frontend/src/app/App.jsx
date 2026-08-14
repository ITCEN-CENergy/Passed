import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Header from '../shared/components/Header.jsx'
import SiteFooter from '../shared/components/SiteFooter.jsx'
import useAuthStore from '../features/auth/model/useAuthStore.js'
import { JobPostingBasket } from '../features/roadmap/components/index.js'
import useRoadmapBasketStore from '../features/roadmap/model/useRoadmapBasketStore.js'
import './App.css'

function App() {
  const { pathname } = useLocation()
  const user = useAuthStore((state) => state.user)
  const isChecking = useAuthStore((state) => state.isChecking)
  const clearBasket = useRoadmapBasketStore((state) => state.clearItems)
  const showRoadmapBasket = pathname === '/job-postings'
    || /^\/job-postings\/\d+\/?$/.test(pathname)
    || pathname === '/recommendations'
    || /^\/recommendations\/[^/]+\/[^/]+\/?$/.test(pathname)

  useEffect(() => {
    if (!isChecking && !user) clearBasket()
  }, [clearBasket, isChecking, user])

  return (
    <div className="app">
      <Header />
      <main className="appMain">
        <Outlet />
      </main>
      {user && showRoadmapBasket && <JobPostingBasket />}
      <SiteFooter />
    </div>
  )
}

export default App

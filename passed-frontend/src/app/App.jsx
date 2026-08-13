import { Outlet } from 'react-router-dom'
import Header from '../shared/components/Header.jsx'
import SiteFooter from '../shared/components/SiteFooter.jsx'
import { JobPostingBasket } from '../features/roadmap/components/index.js'
import './App.css'

function App() {
  return (
    <div className="app">
      <Header />
      <main className="appMain">
        <Outlet />
      </main>
      <JobPostingBasket />
      <SiteFooter />
    </div>
  )
}

export default App

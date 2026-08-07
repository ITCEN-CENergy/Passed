import { createBrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import AboutPage from './pages/AboutPage.jsx'
import HomePage from './pages/HomePage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import CoverLetterResultPage from '../features/cover-letter/pages/CoverLetterResultPage';
// import CoverLetterReviewPage from '../features/cover-letter/pages/CoverLetterReviewPage';
import CompanyCoverLetterWrite from '../features/cover-letter/pages/CompanyCoverLetterWrite';
import CompanyCoverLetterList from '../features/cover-letter/pages/CompanyCoverLetterList';

const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      {
        index: true,
        element: <HomePage />,
      },
      {
        path: 'about',
        element: <AboutPage />,
      },
      {
        path: 'cover-letter-result',
        element: <CoverLetterResultPage />,
      },
      // {
      //   path: 'cover-letter-review',
      //   element: <CoverLetterReviewPage />,
      // },
      {
        path: 'cover-letter-list',
        element: <CompanyCoverLetterList />,
      },
      {
        path: 'cover-letter-write',
        element: <CompanyCoverLetterWrite />,
      },
      {
        path: '*',
        element: <NotFoundPage />,
      },
    ],
  },
])

export default router

import { createBrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import AuthLayout from './AuthLayout.jsx'
import AboutPage from './pages/AboutPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import CoverLetterResultPage from '../features/cover-letter/pages/CoverLetterResultPage';
// import CoverLetterReviewPage from '../features/cover-letter/pages/CoverLetterReviewPage';
import CompanyCoverLetterWrite from '../features/cover-letter/pages/CompanyCoverLetterWrite';
import CompanyCoverLetterList from '../features/cover-letter/pages/CompanyCoverLetterList';
import LoginPage from '../pages/login/LoginPage.jsx'
import SignupPage from '../pages/signup/SignupPage.jsx'
import { JobPostingDetailPage, JobPostingListPage } from '../features/job-posting/pages/index.js'
import { RecommendationDetailPage, RecommendationHistoryPage, RecommendationPage, RecommendationRunResultPage } from '../features/recommendation/pages/index.js'
import { RoadmapDetailPage, RoadmapListPage } from '../features/roadmap/pages/index.js'
import { MyPage } from '../features/user/pages/index.js'

const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      {
        index: true,
        element: <RecommendationPage home />,
      },
      {
        path: 'about',
        element: <AboutPage />,
      },
      {
        path: 'job-postings',
        element: <JobPostingListPage />,
      },
      {
        path: 'job-postings/:jobPostingId',
        element: <JobPostingDetailPage />,
      },
      {
        path: 'recommendations',
        element: <RecommendationPage />,
      },
      {
        path: 'recommendations/:recommendationRunId/:jobRecommendationId',
        element: <RecommendationDetailPage />,
      },
      {
        path: 'mypage/recommendations',
        element: <RecommendationHistoryPage />,
      },
      {
        path: 'mypage/recommendations/:recommendationRunId',
        element: <RecommendationRunResultPage />,
      },
      {
        path: 'roadmap',
        element: <RoadmapListPage />,
      },
      {
        path: 'roadmap/:roadmapId',
        element: <RoadmapDetailPage />,
      },
      {
        path: 'mypage',
        element: <MyPage />,
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
        path: 'cover-letter-write/:coverLetterId',
        element: <CompanyCoverLetterWrite />,
      },
      {
        path: '*',
        element: <NotFoundPage />,
      },
    ],
  },
  {
    element: <AuthLayout />,
    children: [
      {
        path: '/login',
        element: <LoginPage />,
      },
      {
        path: '/signup',
        element: <SignupPage />,
      },
    ],
  },
])

export default router

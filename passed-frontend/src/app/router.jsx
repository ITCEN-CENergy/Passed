import { createBrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import AuthLayout from './AuthLayout.jsx'
import AboutPage from './pages/AboutPage.jsx'
import HomePage from './pages/HomePage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import CoverLetterResultPage from '../features/cover-letter/pages/CoverLetterResultPage';
// import CoverLetterReviewPage from '../features/cover-letter/pages/CoverLetterReviewPage';
import CompanyCoverLetterWrite from '../features/cover-letter/pages/CompanyCoverLetterWrite';
import CompanyCoverLetterList from '../features/cover-letter/pages/CompanyCoverLetterList';
import LoginPage from '../pages/login/LoginPage.jsx'
import SignupPage from '../pages/signup/SignupPage.jsx'
import { JobPostingDetailPage, JobPostingListPage } from '../features/job-posting/pages/index.js'
import { RecommendationDetailPage, RecommendationPage } from '../features/recommendation/pages/index.js'
import { RoadmapDetailPage, RoadmapListPage } from '../features/roadmap/pages/index.js'
import { JobPreferenceOnboardingPage, MyPage } from '../features/user/pages/index.js'
import { ResumeEditorPage } from '../features/resume/pages/index.js'
import CommonCoverLetterPage from '../features/cover-letter/pages/CommonCoverLetterPage.jsx'
import { SkillAnalysisPage, SkillReviewPage } from '../features/skill/pages/index.js'

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
        path: 'onboarding/preferences',
        element: <JobPreferenceOnboardingPage />,
      },
      {
        path: 'onboarding/resume',
        element: <ResumeEditorPage onboarding />,
      },
      {
        path: 'resume',
        element: <ResumeEditorPage />,
      },
      {
        path: 'onboarding/cover-letter',
        element: <CommonCoverLetterPage onboarding />,
      },
      {
        path: 'cover-letter',
        element: <CommonCoverLetterPage />,
      },
      {
        path: 'onboarding/analysis',
        element: <SkillAnalysisPage />,
      },
      {
        path: 'onboarding/skills',
        element: <SkillReviewPage />,
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

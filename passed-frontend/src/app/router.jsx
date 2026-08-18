import { createBrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import AuthLayout from './AuthLayout.jsx'
import AboutPage from './pages/AboutPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import {
  CommonCoverLetterPage,
  CompanyCoverLetterList,
  CompanyCoverLetterWrite,
  CompanyCoverLetterResult,
} from '../features/cover-letter/pages/index.js'
import { LoginPage, SignupPage } from '../features/auth/pages/index.js'
import { JobPostingCreatePage, JobPostingDetailPage, JobPostingListPage } from '../features/job-posting/pages/index.js'
import {
  RecommendationDetailPage,
  RecommendationHistoryPage,
  RecommendationPage,
  RecommendationRunResultPage,
} from '../features/recommendation/pages/index.js'
import { RoadmapDetailPage, RoadmapListPage } from '../features/roadmap/pages/index.js'
import { JobPreferenceOnboardingPage, MyPage } from '../features/user/pages/index.js'
import { ResumeEditorPage } from '../features/resume/pages/index.js'
import { SkillAnalysisPage, SkillReviewPage } from '../features/skill/pages/index.js'

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
        path: 'job-postings/new',
        element: <JobPostingCreatePage />,
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
        element: <CompanyCoverLetterResult />,
      },
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
        element: <CoverLetterWritePage />,
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

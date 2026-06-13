import { createBrowserRouter, Navigate } from 'react-router-dom'
import PrivateRoute from '@/shared/components/PrivateRoute'
import RoleRoute from '@/shared/components/RoleRoute'
import AuthenticatedLayout from './AuthenticatedLayout'
import LoginPage from '@/features/auth/pages/LoginPage'
import SignupPage from '@/features/auth/pages/SignupPage'
import CoursesPage from '@/features/course/pages/CoursesPage'
import CourseDetailPage from '@/features/course/pages/CourseDetailPage'
import MyWaitlistsPage from '@/features/enrollment/pages/MyWaitlistsPage'
import MyEnrollmentsPage from '@/features/enrollment/pages/MyEnrollmentsPage'
import MyProfilePage from '@/features/student/pages/MyProfilePage'
import NotificationsPage from '@/features/notification/pages/NotificationsPage'
import CourseFormPage from '@/features/course/pages/CourseFormPage'
import ProfessorProfilePage from '@/features/professor/pages/ProfessorProfilePage'
import AdminStudentsPage from '@/features/admin/pages/AdminStudentsPage'
import AdminCoursesPage from '@/features/admin/pages/AdminCoursesPage'
import AdminCourseEnrollmentsPage from '@/features/admin/pages/AdminCourseEnrollmentsPage'
import AdminCourseWaitlistsPage from '@/features/admin/pages/AdminCourseWaitlistsPage'
import AdminStatsPage from '@/features/admin/pages/AdminStatsPage'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  {
    element: <PrivateRoute />,
    children: [
      {
        element: <AuthenticatedLayout />,
        children: [
          { path: '/', element: <Navigate to="/courses" replace /> },
          { path: '/courses', element: <CoursesPage /> },
          { path: '/courses/:courseId', element: <CourseDetailPage /> },
          {
            element: <RoleRoute allowedRoles={['STUDENT']} />,
            children: [
              { path: '/enrollments/me', element: <MyEnrollmentsPage /> },
              { path: '/waitlists/me', element: <MyWaitlistsPage /> },
              { path: '/notifications', element: <NotificationsPage /> },
              { path: '/profile', element: <MyProfilePage /> },
            ],
          },
          {
            element: <RoleRoute allowedRoles={['PROFESSOR']} />,
            children: [
              { path: '/professor/courses/new', element: <CourseFormPage /> },
              { path: '/professor/courses/:courseId/edit', element: <CourseFormPage /> },
              { path: '/professor/profile', element: <ProfessorProfilePage /> },
            ],
          },
          {
            element: <RoleRoute allowedRoles={['ADMIN']} />,
            children: [
              { path: '/admin/students', element: <AdminStudentsPage /> },
              { path: '/admin/courses', element: <AdminCoursesPage /> },
              { path: '/admin/courses/:courseId/enrollments', element: <AdminCourseEnrollmentsPage /> },
              { path: '/admin/courses/:courseId/waitlists', element: <AdminCourseWaitlistsPage /> },
              { path: '/admin/stats', element: <AdminStatsPage /> },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

export default router

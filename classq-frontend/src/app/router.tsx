import { createBrowserRouter, Navigate } from 'react-router-dom'
import PrivateRoute from '@/shared/components/PrivateRoute'
import RoleRoute from '@/shared/components/RoleRoute'
import LoginPage from '@/features/auth/pages/LoginPage'
import SignupPage from '@/features/auth/pages/SignupPage'
import CoursesPage from '@/features/course/pages/CoursesPage'
import CourseDetailPage from '@/features/course/pages/CourseDetailPage'
import MyWaitlistsPage from '@/features/enrollment/pages/MyWaitlistsPage'
import MyEnrollmentsPage from '@/features/enrollment/pages/MyEnrollmentsPage'
import MyProfilePage from '@/features/student/pages/MyProfilePage'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  {
    element: <PrivateRoute />,
    children: [
      { path: '/', element: <Navigate to="/courses" replace /> },
      { path: '/courses', element: <CoursesPage /> },
      { path: '/courses/:courseId', element: <CourseDetailPage /> },
      {
        element: <RoleRoute allowedRoles={['STUDENT']} />,
        children: [
          { path: '/enrollments/me', element: <MyEnrollmentsPage /> },
          { path: '/waitlists/me', element: <MyWaitlistsPage /> },
          { path: '/notifications', element: <div>Notifications</div> },
          { path: '/profile', element: <MyProfilePage /> },
        ],
      },
      {
        element: <RoleRoute allowedRoles={['PROFESSOR']} />,
        children: [
          { path: '/professor/courses/new', element: <div>CreateCourse</div> },
          { path: '/professor/courses/:courseId/edit', element: <div>EditCourse</div> },
        ],
      },
      {
        element: <RoleRoute allowedRoles={['ADMIN']} />,
        children: [
          { path: '/admin/students', element: <div>AdminStudents</div> },
          { path: '/admin/courses', element: <div>AdminCourses</div> },
          { path: '/admin/courses/:courseId/enrollments', element: <div>AdminEnrollments</div> },
          { path: '/admin/courses/:courseId/waitlists', element: <div>AdminWaitlists</div> },
          { path: '/admin/stats', element: <div>AdminStats</div> },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

export default router

import { createBrowserRouter, Navigate } from 'react-router-dom'
import PrivateRoute from '@/shared/components/PrivateRoute'
import RoleRoute from '@/shared/components/RoleRoute'
import LoginPage from '@/features/auth/pages/LoginPage'
import SignupPage from '@/features/auth/pages/SignupPage'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  {
    element: <PrivateRoute />,
    children: [
      { path: '/', element: <Navigate to="/courses" replace /> },
      { path: '/courses', element: <div>CourseList</div> },
      { path: '/courses/:courseId', element: <div>CourseDetail</div> },
      {
        element: <RoleRoute allowedRoles={['STUDENT']} />,
        children: [
          { path: '/enrollments/me', element: <div>MyEnrollments</div> },
          { path: '/waitlists/me', element: <div>MyWaitlists</div> },
          { path: '/notifications', element: <div>Notifications</div> },
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

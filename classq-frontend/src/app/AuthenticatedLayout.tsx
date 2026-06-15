import { Outlet, NavLink } from 'react-router-dom'
import { NotificationListener } from '@/features/notification/components/NotificationListener'
import { useLogout } from '@/features/auth/hooks/useLogout'
import { useAuthStore } from '@/shared/store/authStore'

const ROLE_LABEL = {
  STUDENT: '학생',
  PROFESSOR: '교수',
  ADMIN: '관리자',
}

const NAV_LINKS = {
  STUDENT: [
    { to: '/courses', label: '강의 목록' },
    { to: '/enrollments/me', label: '내 수강신청' },
    { to: '/waitlists/me', label: '내 대기 목록' },
    { to: '/notifications', label: '알림' },
    { to: '/profile', label: '내 정보' },
  ],
  PROFESSOR: [
    { to: '/courses', label: '강의 목록' },
    { to: '/professor/courses/new', label: '강의 등록' },
    { to: '/professor/profile', label: '내 정보' },
  ],
  ADMIN: [
    { to: '/admin/students', label: '학생 관리' },
    { to: '/admin/courses', label: '강의 관리' },
    { to: '/admin/professors', label: '교수 승인' },
    { to: '/admin/stats', label: '통계' },
  ],
}

export default function AuthenticatedLayout() {
  const logout = useLogout()
  const role = useAuthStore((state) => state.role)
  const links = role ? NAV_LINKS[role] : []

  return (
    <>
      <NotificationListener />
      <header className="border-b bg-white">
        <div className="flex justify-between items-center px-6 py-3">
          <span className="text-sm font-medium text-gray-700">
            {role ? ROLE_LABEL[role] : ''}
          </span>
          <button
            onClick={logout}
            className="text-sm text-gray-600 hover:text-gray-900"
          >
            로그아웃
          </button>
        </div>
        <nav className="flex gap-1 px-6 pb-2">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) =>
                `px-3 py-1.5 text-sm rounded ${
                  isActive
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-600 hover:bg-gray-100'
                }`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <Outlet />
    </>
  )
}

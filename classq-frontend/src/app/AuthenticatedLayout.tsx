import { Outlet } from 'react-router-dom'
import { NotificationListener } from '@/features/notification/components/NotificationListener'
import { useLogout } from '@/features/auth/hooks/useLogout'

export default function AuthenticatedLayout() {
  const logout = useLogout()

  return (
    <>
      <NotificationListener />
      <header className="flex justify-end px-6 py-3 border-b bg-white">
        <button
          onClick={logout}
          className="text-sm text-gray-600 hover:text-gray-900"
        >
          로그아웃
        </button>
      </header>
      <Outlet />
    </>
  )
}

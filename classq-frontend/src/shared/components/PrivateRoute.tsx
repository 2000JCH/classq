import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/shared/store/authStore'

export default function PrivateRoute() {
  const accessToken = useAuthStore((state) => state.accessToken)
  return accessToken ? <Outlet /> : <Navigate to="/login" replace />
}

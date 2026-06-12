import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/shared/store/authStore'

type Role = 'STUDENT' | 'PROFESSOR' | 'ADMIN'

interface Props {
  allowedRoles: Role[]
}

export default function RoleRoute({ allowedRoles }: Props) {
  const role = useAuthStore((state) => state.role)
  return role && allowedRoles.includes(role) ? <Outlet /> : <Navigate to="/" replace />
}

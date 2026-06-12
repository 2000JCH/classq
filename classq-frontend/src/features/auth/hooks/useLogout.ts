import { useNavigate } from 'react-router-dom'
import api from '@/shared/api/axiosInstance'
import { useAuthStore } from '@/shared/store/authStore'

export function useLogout() {
  const navigate = useNavigate()
  const clearAuth = useAuthStore((state) => state.clearAuth)

  return async () => {
    try {
      await api.post('/auth/logout')
    } finally {
      clearAuth()
      navigate('/login', { replace: true })
    }
  }
}

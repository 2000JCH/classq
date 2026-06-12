import { useNavigate } from 'react-router-dom'
import api from '@/shared/api/axiosInstance'
import { useAuthStore } from '@/shared/store/authStore'

export function useLogout() {
  const navigate = useNavigate()
  const clearAuth = useAuthStore((state) => state.clearAuth)

  return async () => {
    try {
      await api.post('/auth/logout')
    } catch {
      // 서버 실패와 무관하게 항상 로그아웃 처리
    } finally {
      clearAuth()
      navigate('/login', { replace: true })
    }
  }
}

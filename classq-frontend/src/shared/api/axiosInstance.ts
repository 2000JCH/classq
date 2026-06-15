import axios from 'axios'
import { useAuthStore } from '@/shared/store/authStore'

declare module 'axios' {
  interface InternalAxiosRequestConfig {
    _retry?: boolean
  }
}

const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let isRefreshing = false
let queue: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = []

function flushQueue(err: unknown, token: string | null) {
  queue.forEach(({ resolve, reject }) => (err ? reject(err) : resolve(token!)))
  queue = []
}

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error)
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        queue.push({ resolve, reject })
      }).then((token) => {
        original.headers.Authorization = `Bearer ${token}`
        return api(original)
      })
    }

    original._retry = true
    isRefreshing = true

    try {
      const { data } = await axios.post('/api/v1/auth/refresh', {}, { withCredentials: true })
      useAuthStore.getState().setAuth(data.accessToken)
      flushQueue(null, data.accessToken)
      original.headers.Authorization = `Bearer ${data.accessToken}`
      return api(original)
    } catch (err) {
      flushQueue(err, null)
      useAuthStore.getState().clearAuth()
      window.location.href = '/login'
      return Promise.reject(err)
    } finally {
      isRefreshing = false
    }
  }
)

export default api

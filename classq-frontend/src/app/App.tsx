import { useEffect, useState } from 'react'
import { RouterProvider } from 'react-router-dom'
import axios from 'axios'
import router from './router'
import { useAuthStore } from '@/shared/store/authStore'

export default function App() {
  const [ready, setReady] = useState(false)
  const { accessToken, setAuth } = useAuthStore()

  useEffect(() => {
    if (accessToken) {
      setReady(true)
      return
    }

    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 5000)

    axios
      .post('/api/v1/auth/refresh', {}, { withCredentials: true, signal: controller.signal })
      .then(({ data }) => setAuth(data.accessToken))
      .catch(() => {})
      .finally(() => {
        clearTimeout(timer)
        setReady(true)
      })

    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [])

  if (!ready) return null

  return <RouterProvider router={router} />
}

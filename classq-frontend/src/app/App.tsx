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

    axios
      .post('/api/v1/auth/refresh', {}, { withCredentials: true })
      .then(({ data }) => setAuth(data.accessToken))
      .catch(() => {})
      .finally(() => setReady(true))
  }, [])

  if (!ready) return null

  return <RouterProvider router={router} />
}

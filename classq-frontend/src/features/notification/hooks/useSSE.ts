import { useEffect, useRef } from 'react'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { useAuthStore } from '@/shared/store/authStore'
import type { SSEMessage } from '../types/notification'

export function useSSE(onMessage: (msg: SSEMessage) => void) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const role = useAuthStore((state) => state.role)
  const onMessageRef = useRef(onMessage)

  useEffect(() => {
    onMessageRef.current = onMessage
  })

  useEffect(() => {
    if (!accessToken || role !== 'STUDENT') return

    const controller = new AbortController()

    fetchEventSource('/api/v1/notifications/subscribe', {
      headers: { Authorization: `Bearer ${accessToken}` },
      signal: controller.signal,
      onmessage(event) {
        if (!event.data || event.data === 'heartbeat') return
        try {
          const msg: SSEMessage = JSON.parse(event.data)
          onMessageRef.current(msg)
        } catch {
          // 파싱 실패 무시
        }
      },
      onerror() {
        // fetchEventSource가 자동 재연결 처리
      },
    })

    return () => controller.abort()
  }, [accessToken, role])
}

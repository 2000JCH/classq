import { useEffect, useState } from 'react'
import { getNotifications, markAsRead } from '../api/notificationApi'
import type { Notification, NotificationType } from '../types/notification'

const TYPE_LABELS: Record<NotificationType, string> = {
  WAITLIST_AVAILABLE: '대기 수락 가능',
  WAITLIST_EXPIRED: '대기 시간 초과',
  WAITLIST_CANCELLED: '대기 취소',
  COURSE_CLOSED: '강의 폐강',
  CREDIT_EXCEEDED: '학점 초과',
  TIME_CONFLICT: '시간 중복',
}

const TYPE_COLORS: Record<NotificationType, string> = {
  WAITLIST_AVAILABLE: 'bg-yellow-100 text-yellow-700',
  WAITLIST_EXPIRED: 'bg-red-100 text-red-500',
  WAITLIST_CANCELLED: 'bg-gray-100 text-gray-500',
  COURSE_CLOSED: 'bg-red-100 text-red-500',
  CREDIT_EXCEEDED: 'bg-orange-100 text-orange-600',
  TIME_CONFLICT: 'bg-orange-100 text-orange-600',
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    getNotifications()
      .then(setNotifications)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [])

  async function handleMarkAsRead(id: number) {
    try {
      await markAsRead(id)
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n)),
      )
    } catch {
      // 실패 시 무시
    }
  }

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>
  if (error) return <p className="text-center text-red-500 py-20">알림을 불러오지 못했습니다.</p>

  return (
    <div className="max-w-2xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">알림</h1>

      {notifications.length === 0 ? (
        <p className="text-center text-gray-400 py-16">알림이 없습니다.</p>
      ) : (
        <div className="border rounded-lg divide-y">
          {notifications.map((n) => (
            <div
              key={n.id}
              className={`flex items-start gap-3 px-4 py-4 ${n.readAt ? 'bg-white' : 'bg-blue-50'}`}
            >
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span className={`text-xs px-2 py-0.5 rounded-full ${TYPE_COLORS[n.type]}`}>
                    {TYPE_LABELS[n.type]}
                  </span>
                  {!n.readAt && (
                    <span className="text-xs text-blue-600 font-medium">새 알림</span>
                  )}
                </div>
                <p className="text-sm text-gray-800">{n.message}</p>
                <p className="text-xs text-gray-400 mt-1">{formatDate(n.createdAt)}</p>
              </div>
              {!n.readAt && (
                <button
                  className="text-xs text-gray-500 hover:text-gray-700 shrink-0 mt-1"
                  onClick={() => handleMarkAsRead(n.id)}
                >
                  읽음
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

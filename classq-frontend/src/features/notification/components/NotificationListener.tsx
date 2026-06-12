import { Link } from 'react-router-dom'
import { useSSE } from '../hooks/useSSE'
import { useNotificationStore } from '../store/notificationStore'
import type { NotificationType } from '../types/notification'

const TYPE_LABELS: Record<NotificationType, string> = {
  WAITLIST_AVAILABLE: '대기 수락 가능',
  WAITLIST_EXPIRED: '대기 시간 초과',
  WAITLIST_CANCELLED: '대기 취소',
  COURSE_CLOSED: '강의 폐강',
  CREDIT_EXCEEDED: '학점 초과',
  TIME_CONFLICT: '시간 중복',
}

export function NotificationListener() {
  const { alerts, addAlert, removeAlert } = useNotificationStore()

  useSSE((msg) => addAlert(msg))

  if (alerts.length === 0) return null

  return (
    <div className="fixed top-4 right-4 z-50 space-y-2 max-w-sm w-full">
      {alerts.map((alert) => (
        <div
          key={alert.notificationId}
          className="bg-white border rounded-lg shadow-lg p-4 flex items-start gap-3"
        >
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-gray-800">{TYPE_LABELS[alert.type]}</p>
            <p className="text-sm text-gray-600 mt-0.5">{alert.message}</p>
            {alert.type === 'WAITLIST_AVAILABLE' && (
              <Link
                to="/waitlists/me"
                className="text-xs text-blue-600 hover:underline mt-1 inline-block"
                onClick={() => removeAlert(alert.notificationId)}
              >
                대기 목록 확인 →
              </Link>
            )}
          </div>
          <button
            className="text-gray-400 hover:text-gray-600 shrink-0"
            onClick={() => removeAlert(alert.notificationId)}
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  )
}

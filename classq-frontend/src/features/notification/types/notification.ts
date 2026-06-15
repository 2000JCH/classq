export type NotificationType =
  | 'WAITLIST_AVAILABLE'
  | 'WAITLIST_EXPIRED'
  | 'WAITLIST_CANCELLED'
  | 'COURSE_CLOSED'
  | 'CREDIT_EXCEEDED'
  | 'TIME_CONFLICT'

export interface SSEMessage {
  type: NotificationType
  notificationId: number
  message: string
}

export interface Notification {
  id: number
  type: NotificationType
  message: string
  courseId: number
  readAt: string | null
  createdAt: string
}

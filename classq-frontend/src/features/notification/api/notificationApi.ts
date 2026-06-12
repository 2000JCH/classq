import api from '@/shared/api/axiosInstance'
import type { Notification } from '../types/notification'

export async function getNotifications(): Promise<Notification[]> {
  const { data } = await api.get('/notifications')
  return data
}

export async function markAsRead(notificationId: number): Promise<void> {
  await api.patch(`/notifications/${notificationId}/read`)
}

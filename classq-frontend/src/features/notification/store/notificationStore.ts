import { create } from 'zustand'
import type { SSEMessage } from '../types/notification'

interface NotificationStore {
  alerts: SSEMessage[]
  addAlert: (msg: SSEMessage) => void
  removeAlert: (notificationId: number) => void
}

export const useNotificationStore = create<NotificationStore>((set) => ({
  alerts: [],
  addAlert: (msg) => set((state) => ({ alerts: [...state.alerts, msg] })),
  removeAlert: (id) =>
    set((state) => ({ alerts: state.alerts.filter((a) => a.notificationId !== id) })),
}))

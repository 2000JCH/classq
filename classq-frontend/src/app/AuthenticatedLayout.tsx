import { Outlet } from 'react-router-dom'
import { NotificationListener } from '@/features/notification/components/NotificationListener'

export default function AuthenticatedLayout() {
  return (
    <>
      <NotificationListener />
      <Outlet />
    </>
  )
}

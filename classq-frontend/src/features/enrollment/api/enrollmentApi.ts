import api from '@/shared/api/axiosInstance'
import type {
  EnrollmentItem,
  EnrollmentRequest,
  EnrollmentResult,
  MyWaitlistsResponse,
  WaitlistRequest,
} from '../types/enrollment'

export async function enroll(req: EnrollmentRequest): Promise<EnrollmentResult> {
  const { data } = await api.post('/enrollments', req)
  return data
}

export async function cancelEnrollment(enrollmentId: number): Promise<void> {
  await api.delete(`/enrollments/${enrollmentId}`)
}

export async function joinWaitlist(req: WaitlistRequest): Promise<void> {
  await api.post('/waitlists', req)
}

export async function cancelWaitlist(waitlistId: number): Promise<void> {
  await api.delete(`/waitlists/${waitlistId}`)
}

export async function acceptWaitlist(waitlistId: number): Promise<void> {
  await api.post(`/waitlists/${waitlistId}/accept`)
}

export async function rejectWaitlist(waitlistId: number): Promise<void> {
  await api.post(`/waitlists/${waitlistId}/reject`)
}

export async function getMyWaitlists(): Promise<MyWaitlistsResponse> {
  const { data } = await api.get('/waitlists/me')
  return data
}

export async function getMyEnrollments(): Promise<EnrollmentItem[]> {
  const { data } = await api.get('/enrollments/me')
  return data
}

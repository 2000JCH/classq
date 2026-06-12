import api from '@/shared/api/axiosInstance'
import type { EnrollmentRequest, EnrollmentResult, WaitlistRequest } from '../types/enrollment'

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

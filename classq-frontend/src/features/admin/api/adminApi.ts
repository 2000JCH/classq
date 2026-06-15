import api from '@/shared/api/axiosInstance'
import type {
  AdminCourse,
  AdminEnrollmentItem,
  AdminStudent,
  AdminWaitlistItem,
  EnrollmentStats,
  PendingProfessor,
} from '../types/admin'

export async function getAdminStudents(): Promise<AdminStudent[]> {
  const { data } = await api.get('/admin/students')
  return data.content ?? data
}

export async function forceDeleteStudent(studentId: number): Promise<void> {
  await api.delete(`/admin/students/${studentId}`)
}

export async function getAdminCourses(): Promise<AdminCourse[]> {
  const { data } = await api.get('/admin/courses')
  return data.content ?? data
}

export async function forceCloseCourse(courseId: number): Promise<void> {
  await api.delete(`/admin/courses/${courseId}`)
}

export async function getAdminCourseEnrollments(courseId: number): Promise<AdminEnrollmentItem[]> {
  const { data } = await api.get(`/admin/courses/${courseId}/enrollments`)
  return data.content ?? data
}

export async function getAdminCourseWaitlists(courseId: number): Promise<AdminWaitlistItem[]> {
  const { data } = await api.get(`/admin/courses/${courseId}/waitlists`)
  return data.content ?? data
}

export async function getEnrollmentStats(): Promise<EnrollmentStats> {
  const { data } = await api.get('/admin/stats/enrollments')
  return data
}

export async function getPendingProfessors(): Promise<PendingProfessor[]> {
  const { data } = await api.get('/admin/accounts/pending')
  return data
}

export async function approveProfessor(accountId: number): Promise<void> {
  await api.patch(`/admin/accounts/${accountId}/approve`)
}

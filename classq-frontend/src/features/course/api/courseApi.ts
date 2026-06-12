import api from '@/shared/api/axiosInstance'
import type {
  CourseCreateRequest,
  CourseDetail,
  CourseFilters,
  CourseListResponse,
  CourseSchedule,
  Department,
} from '../types/course'

export async function getCourses(filters: CourseFilters): Promise<CourseListResponse> {
  const params = Object.fromEntries(
    Object.entries(filters).filter(([, v]) => v !== undefined && v !== ''),
  )
  const { data } = await api.get('/courses', { params })
  return data
}

export async function getCourse(courseId: number): Promise<CourseDetail> {
  const { data } = await api.get(`/courses/${courseId}`)
  return data
}

export async function getCourseSchedules(courseId: number): Promise<CourseSchedule[]> {
  const { data } = await api.get(`/courses/${courseId}/schedules`)
  return data
}

export async function getDepartments(): Promise<Department[]> {
  const { data } = await api.get('/departments')
  return data
}

export async function createCourse(req: CourseCreateRequest): Promise<void> {
  await api.post('/courses', req)
}

export async function updateCourse(courseId: number, req: CourseCreateRequest): Promise<void> {
  await api.put(`/courses/${courseId}`, req)
}

export async function deleteCourse(courseId: number): Promise<void> {
  await api.delete(`/courses/${courseId}`)
}
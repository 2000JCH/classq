import api from '@/shared/api/axiosInstance'
import type {
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
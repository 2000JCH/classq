export type CourseType = 'MAJOR_REQUIRED' | 'MAJOR_ELECTIVE' | 'LIBERAL_ARTS'
export type ClassType = 'THEORY' | 'PRACTICE'
export type ClassMode = 'ONLINE' | 'OFFLINE'
export type CourseStatus = 'ACTIVE' | 'CLOSED'
export type DayOfWeek = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN'

export interface CourseSchedule {
  id: number
  day: DayOfWeek
  startTime: string
  endTime: string
}

export interface CourseListItem {
  id: number
  name: string
  professorName: string
  departmentName: string | null
  courseType: CourseType
  classType: ClassType
  classMode: ClassMode
  credits: number
  capacity: number
  remainingCapacity: number
  status: CourseStatus
}

export interface CourseDetail {
  id: number
  name: string
  professorName: string
  departmentName: string | null
  courseType: CourseType
  classType: ClassType
  classMode: ClassMode
  credits: number
  capacity: number
  remainingCapacity: number
  waitlistLimit: number
  minGrade: number | null
  maxGrade: number | null
  status: CourseStatus
}

export interface CourseListResponse {
  content: CourseListItem[]
  totalPages: number
  totalElements: number
  number: number
}

export interface Department {
  id: number
  name: string
}

export interface CourseFilters {
  courseType?: CourseType
  classType?: ClassType
  classMode?: ClassMode
  departmentId?: number
  page: number
  size: number
}

export interface CourseScheduleRequest {
  day: DayOfWeek
  startTime: string
  endTime: string
}

export interface CourseCreateRequest {
  name: string
  courseType: CourseType
  classType: ClassType
  classMode: ClassMode
  departmentId?: number
  credits: number
  capacity: number
  waitlistLimit: number
  minGrade?: number
  maxGrade?: number
  schedules: CourseScheduleRequest[]
}

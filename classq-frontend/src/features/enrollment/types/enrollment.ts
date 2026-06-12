export interface EnrollmentRequest {
  courseId: number
}

export interface EnrollmentResult {
  status: 'SUCCESS' | 'FAIL'
  message: string
  courseId?: number
  courseName?: string
}

export interface WaitlistRequest {
  courseId: number
}
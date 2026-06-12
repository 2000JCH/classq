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

export type WaitlistStatus = 'WAITING' | 'NOTIFIED' | 'EXPIRED' | 'COMPLETED'

export interface WaitlistItem {
  waitlistId: number
  courseId: number
  courseName: string
  rank: number
  status: WaitlistStatus
}

export interface MyWaitlistsResponse {
  waitlists: WaitlistItem[]
  currentCredits: number
  maxCredits: number
}
export interface AdminStudent {
  id: number
  name: string
  email: string
  grade: number
  departmentName: string
}

export interface AdminCourse {
  id: number
  name: string
  professorName: string
  departmentName: string | null
  credits: number
  capacity: number
  remainingCapacity: number
  status: 'ACTIVE' | 'CLOSED'
}

export interface AdminEnrollmentItem {
  enrollmentId: number
  studentId: number
  studentName: string
  enrolledAt: string
  status: 'COMPLETED' | 'CANCELLED'
}

export interface AdminWaitlistItem {
  waitlistId: number
  studentId: number
  studentName: string
  rank: number
  status: 'WAITING' | 'NOTIFIED' | 'EXPIRED' | 'COMPLETED'
}

export interface EnrollmentStats {
  todayCount: number
  totalCount: number
  cancelledCount: number
}

export interface PendingProfessor {
  accountId: number
  email: string
  name: string
  departmentName: string
}

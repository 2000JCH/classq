export interface StudentProfile {
  email: string
  name: string
  grade: number
  departmentName: string
}

export interface StudentUpdateRequest {
  name: string
  grade: number
}

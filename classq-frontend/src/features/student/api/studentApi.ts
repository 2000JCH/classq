import api from '@/shared/api/axiosInstance'
import type { StudentProfile, StudentUpdateRequest } from '../types/student'

export async function getMyProfile(): Promise<StudentProfile> {
  const { data } = await api.get('/students/me')
  return data
}

export async function updateMyProfile(req: StudentUpdateRequest): Promise<void> {
  await api.put('/students/me', req)
}

export async function deleteMyAccount(): Promise<void> {
  await api.delete('/students/me')
}

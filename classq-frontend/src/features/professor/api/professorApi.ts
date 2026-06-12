import api from '@/shared/api/axiosInstance'
import type { ProfessorProfile, ProfessorUpdateRequest } from '../types/professor'

export async function getProfessorProfile(): Promise<ProfessorProfile> {
  const { data } = await api.get('/professors/me')
  return data
}

export async function updateProfessorProfile(req: ProfessorUpdateRequest): Promise<void> {
  await api.put('/professors/me', req)
}

import { create } from 'zustand'

type Role = 'STUDENT' | 'PROFESSOR' | 'ADMIN'

interface AuthState {
  accessToken: string | null
  role: Role | null
  setAuth: (token: string) => void
  clearAuth: () => void
}

function decodeRole(token: string): Role {
  const payload = JSON.parse(atob(token.split('.')[1]))
  return payload.role
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  role: null,
  setAuth: (token) => set({ accessToken: token, role: decodeRole(token) }),
  clearAuth: () => set({ accessToken: null, role: null }),
}))

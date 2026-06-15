import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import api from '@/shared/api/axiosInstance'
import { useAuthStore } from '@/shared/store/authStore'
import { getDepartments } from '@/features/course/api/courseApi'
import type { Department } from '@/features/course/types/course'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

const schema = z.object({
  email: z.string().email('올바른 이메일을 입력하세요'),
  password: z.string().min(8, '비밀번호는 8자 이상이어야 합니다'),
  role: z.enum(['STUDENT', 'PROFESSOR']),
  name: z.string().min(1, '이름을 입력하세요'),
  departmentId: z.coerce.number({ invalid_type_error: '학과를 선택하세요' }).min(1, '학과를 선택하세요'),
  grade: z.coerce.number().optional(),
})

type FormData = z.infer<typeof schema>

export default function SignupPage() {
  const navigate = useNavigate()
  const accessToken = useAuthStore((state) => state.accessToken)
  const [departments, setDepartments] = useState<Department[]>([])

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { role: 'STUDENT' },
  })

  const role = watch('role')

  useEffect(() => {
    getDepartments().then(setDepartments).catch(() => {})
  }, [])

  if (accessToken) return <Navigate to="/" replace />

  const onSubmit = async (data: FormData) => {
    if (data.role === 'STUDENT' && !data.grade) {
      setError('grade', { message: '학년을 입력하세요' })
      return
    }
    try {
      await api.post('/auth/signup', data)
      navigate('/login', { replace: true })
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code
      if (code === 'EMAIL_ALREADY_EXISTS') {
        setError('email', { message: '이미 사용 중인 이메일입니다.' })
      } else {
        setError('root', { message: '회원가입에 실패했습니다.' })
      }
    }
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-2xl text-center">회원가입</CardTitle>
        </CardHeader>
        <CardContent>
          <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-1">
              <Label htmlFor="email">이메일</Label>
              <Input id="email" type="email" placeholder="이메일" {...register('email')} />
              {errors.email && <p className="text-sm text-red-500">{errors.email.message}</p>}
            </div>
            <div className="space-y-1">
              <Label htmlFor="password">비밀번호</Label>
              <Input
                id="password"
                type="password"
                placeholder="비밀번호 (8자 이상)"
                {...register('password')}
              />
              {errors.password && <p className="text-sm text-red-500">{errors.password.message}</p>}
            </div>
            <div className="space-y-1">
              <Label>역할</Label>
              <div className="flex gap-6">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input type="radio" value="STUDENT" {...register('role')} />
                  학생
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input type="radio" value="PROFESSOR" {...register('role')} />
                  교수
                </label>
              </div>
            </div>
            <div className="space-y-1">
              <Label htmlFor="name">이름</Label>
              <Input id="name" type="text" placeholder="이름" {...register('name')} />
              {errors.name && <p className="text-sm text-red-500">{errors.name.message}</p>}
            </div>
            <div className="space-y-1">
              <Label htmlFor="departmentId">학과</Label>
              <select
                id="departmentId"
                className="w-full border rounded px-3 py-2 text-sm"
                {...register('departmentId')}
              >
                <option value="">학과 선택</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
              {errors.departmentId && <p className="text-sm text-red-500">{errors.departmentId.message}</p>}
            </div>
            {role === 'STUDENT' && (
              <div className="space-y-1">
                <Label htmlFor="grade">학년</Label>
                <Input id="grade" type="number" placeholder="학년 (1~4)" min={1} max={4} {...register('grade')} />
                {errors.grade && <p className="text-sm text-red-500">{errors.grade.message}</p>}
              </div>
            )}
            {errors.root && (
              <p className="text-sm text-red-500 text-center">{errors.root.message}</p>
            )}
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? '가입 중...' : '회원가입'}
            </Button>
            <p className="text-sm text-center text-gray-500">
              이미 계정이 있으신가요?{' '}
              <Link to="/login" className="text-blue-600 hover:underline">
                로그인
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

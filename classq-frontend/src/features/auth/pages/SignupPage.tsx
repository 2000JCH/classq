import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import api from '@/shared/api/axiosInstance'
import { useAuthStore } from '@/shared/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

const schema = z.object({
  email: z.string().email('올바른 이메일을 입력하세요'),
  password: z.string().min(8, '비밀번호는 8자 이상이어야 합니다'),
  role: z.enum(['STUDENT', 'PROFESSOR']),
})

type FormData = z.infer<typeof schema>

export default function SignupPage() {
  const navigate = useNavigate()
  const accessToken = useAuthStore((state) => state.accessToken)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { role: 'STUDENT' },
  })

  if (accessToken) return <Navigate to="/" replace />

  const onSubmit = async (data: FormData) => {
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
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
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

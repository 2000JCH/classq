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
  password: z.string().min(1, '비밀번호를 입력하세요'),
})

type FormData = z.infer<typeof schema>

export default function LoginPage() {
  const navigate = useNavigate()
  const { accessToken, setAuth } = useAuthStore()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  if (accessToken) return <Navigate to="/" replace />

  const onSubmit = async (data: FormData) => {
    try {
      const { data: res } = await api.post('/auth/login', data)
      setAuth(res.accessToken)
      navigate('/', { replace: true })
    } catch {
      setError('root', { message: '이메일 또는 비밀번호가 올바르지 않습니다.' })
    }
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-2xl text-center">ClassQ</CardTitle>
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
              <Input id="password" type="password" placeholder="비밀번호" {...register('password')} />
              {errors.password && <p className="text-sm text-red-500">{errors.password.message}</p>}
            </div>
            {errors.root && (
              <p className="text-sm text-red-500 text-center">{errors.root.message}</p>
            )}
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? '로그인 중...' : '로그인'}
            </Button>
            <p className="text-sm text-center text-gray-500">
              계정이 없으신가요?{' '}
              <Link to="/signup" className="text-blue-600 hover:underline">
                회원가입
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

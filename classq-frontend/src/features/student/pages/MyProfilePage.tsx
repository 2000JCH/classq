import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { getMyProfile, updateMyProfile, deleteMyAccount } from '../api/studentApi'
import { useAuthStore } from '@/shared/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { StudentProfile } from '../types/student'

const schema = z.object({
  name: z.string().min(1, '이름을 입력하세요'),
  grade: z.coerce.number().int().min(1, '학년을 입력하세요'),
})

type FormData = z.infer<typeof schema>

export default function MyProfilePage() {
  const navigate = useNavigate()
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const [profile, setProfile] = useState<StudentProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [saveMessage, setSaveMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  useEffect(() => {
    getMyProfile()
      .then((data) => {
        setProfile(data)
        reset({ name: data.name, grade: data.grade })
      })
      .finally(() => setLoading(false))
  }, [reset])

  async function onSubmit(data: FormData) {
    setSaveMessage(null)
    try {
      await updateMyProfile(data)
      setProfile((prev) => prev && { ...prev, ...data })
      reset(data)
      setSaveMessage({ type: 'success', text: '저장되었습니다.' })
    } catch {
      setSaveMessage({ type: 'error', text: '저장에 실패했습니다.' })
    }
  }

  async function handleDelete() {
    setIsDeleting(true)
    try {
      await deleteMyAccount()
      clearAuth()
      navigate('/login', { replace: true })
    } catch {
      setSaveMessage({ type: 'error', text: '회원 탈퇴에 실패했습니다.' })
      setShowDeleteConfirm(false)
    } finally {
      setIsDeleting(false)
    }
  }

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>
  if (!profile) return null

  return (
    <div className="max-w-lg mx-auto p-6 space-y-8">
      <h1 className="text-2xl font-bold">내 정보</h1>

      <div className="border rounded-lg p-5 space-y-4">
        <div className="flex gap-4">
          <span className="w-20 text-sm text-gray-500 shrink-0">이메일</span>
          <span className="text-sm">{profile.email}</span>
        </div>
        <div className="flex gap-4">
          <span className="w-20 text-sm text-gray-500 shrink-0">학과</span>
          <span className="text-sm">{profile.departmentName}</span>
        </div>
      </div>

      <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <h2 className="text-lg font-semibold">정보 수정</h2>
        <div className="space-y-1">
          <Label htmlFor="name">이름</Label>
          <Input id="name" {...register('name')} />
          {errors.name && <p className="text-sm text-red-500">{errors.name.message}</p>}
        </div>
        <div className="space-y-1">
          <Label htmlFor="grade">학년</Label>
          <Input id="grade" type="number" min={1} {...register('grade')} />
          {errors.grade && <p className="text-sm text-red-500">{errors.grade.message}</p>}
        </div>
        {saveMessage && (
          <p className={`text-sm ${saveMessage.type === 'success' ? 'text-green-600' : 'text-red-500'}`}>
            {saveMessage.text}
          </p>
        )}
        <Button type="submit" disabled={isSubmitting || !isDirty}>
          {isSubmitting ? '저장 중...' : '저장'}
        </Button>
      </form>

      <div className="border border-red-200 rounded-lg p-5 space-y-3">
        <h2 className="text-lg font-semibold text-red-600">회원 탈퇴</h2>
        <p className="text-sm text-gray-500">탈퇴 시 수강신청 내역이 모두 삭제됩니다.</p>
        {!showDeleteConfirm ? (
          <Button variant="outline" className="text-red-600 border-red-300 hover:bg-red-50" onClick={() => setShowDeleteConfirm(true)}>
            회원 탈퇴
          </Button>
        ) : (
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() => setShowDeleteConfirm(false)}
              disabled={isDeleting}
            >
              취소
            </Button>
            <Button
              className="bg-red-600 hover:bg-red-700 text-white"
              onClick={handleDelete}
              disabled={isDeleting}
            >
              {isDeleting ? '처리 중...' : '탈퇴 확인'}
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}

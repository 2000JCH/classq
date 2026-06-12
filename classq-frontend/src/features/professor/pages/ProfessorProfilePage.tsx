import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { getProfessorProfile, updateProfessorProfile } from '../api/professorApi'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { ProfessorProfile } from '../types/professor'

const schema = z.object({
  name: z.string().min(1, '이름을 입력하세요'),
})

type FormData = z.infer<typeof schema>

export default function ProfessorProfilePage() {
  const [profile, setProfile] = useState<ProfessorProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [saveMessage, setSaveMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  useEffect(() => {
    getProfessorProfile()
      .then((data) => {
        setProfile(data)
        reset({ name: data.name })
      })
      .catch(() => setLoadError(true))
      .finally(() => setLoading(false))
  }, [reset])

  async function onSubmit(data: FormData) {
    setSaveMessage(null)
    try {
      await updateProfessorProfile(data)
      setProfile((prev) => prev && { ...prev, ...data })
      reset(data)
      setSaveMessage({ type: 'success', text: '저장되었습니다.' })
    } catch {
      setSaveMessage({ type: 'error', text: '저장에 실패했습니다.' })
    }
  }

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>
  if (loadError || !profile)
    return <p className="text-center text-red-500 py-20">정보를 불러오지 못했습니다.</p>

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
        {saveMessage && (
          <p className={`text-sm ${saveMessage.type === 'success' ? 'text-green-600' : 'text-red-500'}`}>
            {saveMessage.text}
          </p>
        )}
        <Button type="submit" disabled={isSubmitting || !isDirty}>
          {isSubmitting ? '저장 중...' : '저장'}
        </Button>
      </form>
    </div>
  )
}

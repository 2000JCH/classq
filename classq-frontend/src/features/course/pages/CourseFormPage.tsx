import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useForm, useFieldArray } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import {
  getCourse,
  getCourseSchedules,
  getDepartments,
  createCourse,
  updateCourse,
  deleteCourse,
} from '../api/courseApi'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { Department } from '../types/course'

const scheduleSchema = z
  .object({
    day: z.enum(['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']),
    startTime: z.string().min(1, '시작 시간을 입력하세요'),
    endTime: z.string().min(1, '종료 시간을 입력하세요'),
  })
  .refine((d) => d.startTime < d.endTime, {
    message: '종료 시간은 시작 시간보다 늦어야 합니다',
    path: ['endTime'],
  })

const schema = z.object({
  name: z.string().min(1, '강의명을 입력하세요'),
  courseType: z.enum(['MAJOR_REQUIRED', 'MAJOR_ELECTIVE', 'LIBERAL_ARTS']),
  classType: z.enum(['THEORY', 'PRACTICE']),
  classMode: z.enum(['ONLINE', 'OFFLINE']),
  departmentId: z.coerce.number().optional(),
  credits: z.coerce.number().int().min(1, '1 이상').max(9, '9 이하'),
  capacity: z.coerce.number().int().min(1, '1 이상'),
  waitlistLimit: z.coerce.number().int().min(0, '0 이상'),
  minGrade: z.coerce.number().int().min(1).optional().or(z.literal('')),
  maxGrade: z.coerce.number().int().min(1).optional().or(z.literal('')),
  schedules: z.array(scheduleSchema).min(1, '시간표를 최소 1개 추가하세요'),
})

type FormData = z.infer<typeof schema>

const DAY_OPTIONS = [
  { value: 'MON', label: '월' },
  { value: 'TUE', label: '화' },
  { value: 'WED', label: '수' },
  { value: 'THU', label: '목' },
  { value: 'FRI', label: '금' },
  { value: 'SAT', label: '토' },
  { value: 'SUN', label: '일' },
]

export default function CourseFormPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const isEdit = !!courseId
  const navigate = useNavigate()
  const [departments, setDepartments] = useState<Department[]>([])
  const [loading, setLoading] = useState(isEdit)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      courseType: 'MAJOR_REQUIRED',
      classType: 'THEORY',
      classMode: 'OFFLINE',
      credits: 3,
      capacity: 30,
      waitlistLimit: 0,
      schedules: [{ day: 'MON', startTime: '', endTime: '' }],
    },
  })

  const { fields, append, remove } = useFieldArray({ control, name: 'schedules' })

  useEffect(() => {
    getDepartments().then(setDepartments).catch(() => {})
  }, [])

  useEffect(() => {
    if (!isEdit || !courseId) return
    const id = Number(courseId)

    Promise.all([getCourse(id), getCourseSchedules(id)])
      .then(([course, schedules]) => {
        reset({
          name: course.name,
          courseType: course.courseType,
          classType: course.classType,
          classMode: course.classMode,
          credits: course.credits,
          capacity: course.capacity,
          waitlistLimit: course.waitlistLimit,
          minGrade: course.minGrade ?? undefined,
          maxGrade: course.maxGrade ?? undefined,
          schedules: schedules.map((s) => ({ day: s.day, startTime: s.startTime, endTime: s.endTime })),
        })
      })
      .finally(() => setLoading(false))
  }, [courseId, isEdit, reset])

  async function onSubmit(data: FormData) {
    const payload = {
      ...data,
      departmentId: data.departmentId || undefined,
      minGrade: data.minGrade === '' ? undefined : (data.minGrade as number | undefined),
      maxGrade: data.maxGrade === '' ? undefined : (data.maxGrade as number | undefined),
    }

    if (isEdit) {
      await updateCourse(Number(courseId), payload)
      navigate(`/courses/${courseId}`)
    } else {
      await createCourse(payload)
      navigate('/courses')
    }
  }

  async function handleDelete() {
    setIsDeleting(true)
    try {
      await deleteCourse(Number(courseId))
      navigate('/courses')
    } catch {
      setIsDeleting(false)
      setShowDeleteConfirm(false)
    }
  }

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>

  return (
    <div className="max-w-2xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">{isEdit ? '강의 수정' : '강의 등록'}</h1>

      <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        <div className="space-y-1">
          <Label htmlFor="name">강의명</Label>
          <Input id="name" {...register('name')} />
          {errors.name && <p className="text-sm text-red-500">{errors.name.message}</p>}
        </div>

        <div className="grid grid-cols-3 gap-4">
          <div className="space-y-1">
            <Label>강의 유형</Label>
            <select className="w-full border rounded px-3 py-2 text-sm" {...register('courseType')}>
              <option value="MAJOR_REQUIRED">전공필수</option>
              <option value="MAJOR_ELECTIVE">전공선택</option>
              <option value="LIBERAL_ARTS">교양</option>
            </select>
          </div>
          <div className="space-y-1">
            <Label>수업 유형</Label>
            <select className="w-full border rounded px-3 py-2 text-sm" {...register('classType')}>
              <option value="THEORY">이론</option>
              <option value="PRACTICE">실습</option>
            </select>
          </div>
          <div className="space-y-1">
            <Label>수업 방식</Label>
            <select className="w-full border rounded px-3 py-2 text-sm" {...register('classMode')}>
              <option value="OFFLINE">오프라인</option>
              <option value="ONLINE">온라인</option>
            </select>
          </div>
        </div>

        <div className="space-y-1">
          <Label>학과 (교양이면 선택 안 함)</Label>
          <select className="w-full border rounded px-3 py-2 text-sm" {...register('departmentId')}>
            <option value="">교양 (학과 없음)</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>{d.name}</option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-3 gap-4">
          <div className="space-y-1">
            <Label htmlFor="credits">학점</Label>
            <Input id="credits" type="number" min={1} max={9} {...register('credits')} />
            {errors.credits && <p className="text-sm text-red-500">{errors.credits.message}</p>}
          </div>
          <div className="space-y-1">
            <Label htmlFor="capacity">수강 정원</Label>
            <Input id="capacity" type="number" min={1} {...register('capacity')} />
            {errors.capacity && <p className="text-sm text-red-500">{errors.capacity.message}</p>}
          </div>
          <div className="space-y-1">
            <Label htmlFor="waitlistLimit">대기 정원</Label>
            <Input id="waitlistLimit" type="number" min={0} {...register('waitlistLimit')} />
            {errors.waitlistLimit && <p className="text-sm text-red-500">{errors.waitlistLimit.message}</p>}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1">
            <Label htmlFor="minGrade">최소 학년 (선택)</Label>
            <Input id="minGrade" type="number" min={1} placeholder="제한 없음" {...register('minGrade')} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="maxGrade">최대 학년 (선택)</Label>
            <Input id="maxGrade" type="number" min={1} placeholder="제한 없음" {...register('maxGrade')} />
          </div>
        </div>

        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <Label>시간표</Label>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => append({ day: 'MON', startTime: '', endTime: '' })}
            >
              + 추가
            </Button>
          </div>
          {errors.schedules?.root && (
            <p className="text-sm text-red-500">{errors.schedules.root.message}</p>
          )}
          {errors.schedules?.message && (
            <p className="text-sm text-red-500">{errors.schedules.message}</p>
          )}
          {fields.map((field, index) => (
            <div key={field.id} className="flex items-start gap-2">
              <select
                className="border rounded px-2 py-2 text-sm w-20"
                {...register(`schedules.${index}.day`)}
              >
                {DAY_OPTIONS.map((d) => (
                  <option key={d.value} value={d.value}>{d.label}</option>
                ))}
              </select>
              <div className="flex-1 space-y-1">
                <Input type="time" {...register(`schedules.${index}.startTime`)} />
                {errors.schedules?.[index]?.startTime && (
                  <p className="text-xs text-red-500">{errors.schedules[index].startTime?.message}</p>
                )}
              </div>
              <div className="flex-1 space-y-1">
                <Input type="time" {...register(`schedules.${index}.endTime`)} />
                {errors.schedules?.[index]?.endTime && (
                  <p className="text-xs text-red-500">{errors.schedules[index].endTime?.message}</p>
                )}
              </div>
              {fields.length > 1 && (
                <Button type="button" variant="outline" size="sm" onClick={() => remove(index)}>
                  삭제
                </Button>
              )}
            </div>
          ))}
        </div>

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? '처리 중...' : isEdit ? '수정 완료' : '강의 등록'}
        </Button>
      </form>

      {isEdit && (
        <div className="mt-8 border border-red-200 rounded-lg p-5 space-y-3">
          <h2 className="text-lg font-semibold text-red-600">강의 폐강</h2>
          <p className="text-sm text-gray-500">폐강 시 수강/대기 학생에게 알림이 발송됩니다.</p>
          {!showDeleteConfirm ? (
            <Button
              variant="outline"
              className="text-red-600 border-red-300 hover:bg-red-50"
              onClick={() => setShowDeleteConfirm(true)}
            >
              폐강
            </Button>
          ) : (
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setShowDeleteConfirm(false)} disabled={isDeleting}>
                취소
              </Button>
              <Button
                className="bg-red-600 hover:bg-red-700 text-white"
                onClick={handleDelete}
                disabled={isDeleting}
              >
                {isDeleting ? '처리 중...' : '폐강 확인'}
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

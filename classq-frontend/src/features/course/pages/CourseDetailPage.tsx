import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getCourse, getCourseSchedules } from '../api/courseApi'
import { enroll, joinWaitlist } from '@/features/enrollment/api/enrollmentApi'
import { useAuthStore } from '@/shared/store/authStore'
import { Button } from '@/components/ui/button'
import type {
  ClassMode,
  ClassType,
  CourseDetail,
  CourseSchedule,
  CourseType,
  DayOfWeek,
} from '../types/course'

const COURSE_TYPE_LABELS: Record<CourseType, string> = {
  MAJOR_REQUIRED: '전공필수',
  MAJOR_ELECTIVE: '전공선택',
  LIBERAL_ARTS: '교양',
}

const CLASS_TYPE_LABELS: Record<ClassType, string> = {
  THEORY: '이론',
  PRACTICE: '실습',
}

const CLASS_MODE_LABELS: Record<ClassMode, string> = {
  ONLINE: '온라인',
  OFFLINE: '오프라인',
}

const DAY_LABELS: Record<DayOfWeek, string> = {
  MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토', SUN: '일',
}

const DAY_ORDER: DayOfWeek[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

const ERROR_MESSAGES: Record<string, string> = {
  ENROLLMENT_CLOSED: '수강 자리가 마감되었습니다.',
  WAITLIST_CLOSED: '대기 자리가 마감되었습니다.',
  DUPLICATE_ENROLLMENT: '이미 신청한 강의입니다.',
  CREDIT_EXCEEDED: '학점이 초과되었습니다.',
  TIME_CONFLICT: '시간표가 중복됩니다.',
  ENROLLMENT_LOCKED: '대기자 처리 중입니다. 아래 대기자 등록 버튼을 이용해주세요.',
}

function getErrorMessage(err: unknown): string {
  const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code
  return (code && ERROR_MESSAGES[code]) ?? '요청에 실패했습니다.'
}

export default function CourseDetailPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const role = useAuthStore((state) => state.role)
  const [course, setCourse] = useState<CourseDetail | null>(null)
  const [schedules, setSchedules] = useState<CourseSchedule[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [actionMessage, setActionMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [enrollmentLocked, setEnrollmentLocked] = useState(false)

  useEffect(() => {
    if (!courseId) return
    const id = Number(courseId)

    setCourse(null)
    setSchedules([])
    setError(false)
    setLoading(true)
    setActionMessage(null)

    Promise.all([getCourse(id), getCourseSchedules(id)])
      .then(([courseData, schedulesData]) => {
        setCourse(courseData)
        setSchedules(
          schedulesData.sort((a, b) => DAY_ORDER.indexOf(a.day) - DAY_ORDER.indexOf(b.day)),
        )
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [courseId])

  async function handleEnroll() {
    if (!course) return
    setIsSubmitting(true)
    setActionMessage(null)
    try {
      const result = await enroll({ courseId: course.id })
      if (result.status === 'FAIL') {
        setActionMessage({ type: 'error', text: result.message })
      } else {
        setActionMessage({ type: 'success', text: '수강신청이 완료되었습니다.' })
        const updated = await getCourse(course.id)
        setCourse(updated)
      }
    } catch (err) {
      const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code
      if (code === 'ENROLLMENT_LOCKED') setEnrollmentLocked(true)
      setActionMessage({ type: 'error', text: getErrorMessage(err) })
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleJoinWaitlist() {
    if (!course) return
    setIsSubmitting(true)
    setActionMessage(null)
    try {
      await joinWaitlist({ courseId: course.id })
      setActionMessage({ type: 'success', text: '대기자 등록이 완료되었습니다.' })
      const updated = await getCourse(course.id)
      setCourse(updated)
    } catch (err) {
      setActionMessage({ type: 'error', text: getErrorMessage(err) })
    } finally {
      setIsSubmitting(false)
    }
  }

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>
  if (error || !course) {
    return (
      <div className="text-center py-20">
        <p className="text-gray-500 mb-4">강의 정보를 불러올 수 없습니다.</p>
        <Link to="/courses" className="text-blue-600 hover:underline text-sm">
          목록으로
        </Link>
      </div>
    )
  }

  const gradeRange =
    course.minGrade && course.maxGrade
      ? `${course.minGrade}~${course.maxGrade}학년`
      : course.minGrade
        ? `${course.minGrade}학년 이상`
        : course.maxGrade
          ? `${course.maxGrade}학년 이하`
          : '제한 없음'

  const showEnrollButton =
    role === 'STUDENT' && course.status === 'ACTIVE' && course.remainingCapacity > 0

  const showWaitlistButton =
    role === 'STUDENT' &&
    course.status === 'ACTIVE' &&
    (course.remainingCapacity === 0 || enrollmentLocked) &&
    course.waitlistLimit > 0

  return (
    <div className="max-w-3xl mx-auto p-6">
      <Link to="/courses" className="text-sm text-blue-600 hover:underline mb-4 inline-block">
        ← 목록으로
      </Link>

      <div className="flex items-start justify-between mb-6">
        <h1 className="text-2xl font-bold">{course.name}</h1>
        <div className="flex items-center gap-2">
          {(() => {
            const label =
              course.status !== 'ACTIVE' ? '마감' :
              course.remainingCapacity > 0 ? '신청 가능' :
              course.waitlistLimit > 0 && course.remainingWaitlist > 0 ? '대기 가능' : '마감'
            const color =
              label === '신청 가능' ? 'bg-green-100 text-green-700' :
              label === '대기 가능' ? 'bg-yellow-100 text-yellow-700' :
              'bg-red-100 text-red-700'
            return <span className={`text-sm px-3 py-1 rounded-full ${color}`}>{label}</span>
          })()}
          {role === 'PROFESSOR' && (
            <Link
              to={`/professor/courses/${courseId}/edit`}
              className="text-sm px-3 py-1 rounded border border-gray-300 hover:bg-gray-50"
            >
              수정
            </Link>
          )}
        </div>
      </div>

      <div className="border rounded-lg divide-y mb-6">
        <InfoRow label="교수" value={course.professorName} />
        <InfoRow label="학과" value={course.departmentName ?? '교양 (학과 제한 없음)'} />
        <InfoRow label="강의 유형" value={COURSE_TYPE_LABELS[course.courseType]} />
        <InfoRow label="수업 유형" value={CLASS_TYPE_LABELS[course.classType]} />
        <InfoRow label="수업 방식" value={CLASS_MODE_LABELS[course.classMode]} />
        <InfoRow label="학점" value={`${course.credits}학점`} />
        <InfoRow
          label="수강 인원"
          value={`${course.remainingCapacity}/${course.capacity} (대기 최대 ${course.waitlistLimit}명)`}
        />
        <InfoRow label="수강 학년" value={gradeRange} />
      </div>

      <h2 className="text-lg font-semibold mb-3">시간표</h2>
      {schedules.length === 0 ? (
        <p className="text-gray-400 text-sm mb-6">시간표 정보가 없습니다.</p>
      ) : (
        <div className="border rounded-lg overflow-hidden mb-6">
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-gray-600">요일</th>
                <th className="px-4 py-3 text-left font-medium text-gray-600">시작</th>
                <th className="px-4 py-3 text-left font-medium text-gray-600">종료</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {schedules.map((s) => (
                <tr key={s.id}>
                  <td className="px-4 py-3 text-gray-700">{DAY_LABELS[s.day]}</td>
                  <td className="px-4 py-3 text-gray-700">{s.startTime}</td>
                  <td className="px-4 py-3 text-gray-700">{s.endTime}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {(showEnrollButton || showWaitlistButton) && (
        <div className="space-y-3">
          {actionMessage && (
            <p
              className={`text-sm text-center ${
                actionMessage.type === 'success' ? 'text-green-600' : 'text-red-500'
              }`}
            >
              {actionMessage.text}
            </p>
          )}
          {showEnrollButton && (
            <Button className="w-full" disabled={isSubmitting} onClick={handleEnroll}>
              {isSubmitting ? '처리 중...' : '수강신청'}
            </Button>
          )}
          {showWaitlistButton && (
            <Button
              variant="outline"
              className="w-full"
              disabled={isSubmitting}
              onClick={handleJoinWaitlist}
            >
              {isSubmitting ? '처리 중...' : '대기자 등록'}
            </Button>
          )}
        </div>
      )}
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex px-4 py-3 gap-4">
      <span className="w-28 text-sm text-gray-500 shrink-0">{label}</span>
      <span className="text-sm text-gray-900">{value}</span>
    </div>
  )
}

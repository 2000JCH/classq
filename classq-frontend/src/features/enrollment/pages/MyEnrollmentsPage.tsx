import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyEnrollments, cancelEnrollment } from '../api/enrollmentApi'
import { Button } from '@/components/ui/button'
import type { EnrollmentItem } from '../types/enrollment'

export default function MyEnrollmentsPage() {
  const [enrollments, setEnrollments] = useState<EnrollmentItem[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState<number | null>(null)

  async function load() {
    try {
      const data = await getMyEnrollments()
      setEnrollments(data.filter((e) => e.status === 'COMPLETED'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const majorCredits = enrollments
    .filter((e) => e.courseType === 'MAJOR_REQUIRED' || e.courseType === 'MAJOR_ELECTIVE')
    .reduce((sum, e) => sum + e.credits, 0)
  const liberalCredits = enrollments
    .filter((e) => e.courseType === 'LIBERAL_ARTS')
    .reduce((sum, e) => sum + e.credits, 0)
  const totalCredits = majorCredits + liberalCredits

  async function handleCancel(enrollmentId: number) {
    setSubmitting(enrollmentId)
    try {
      await cancelEnrollment(enrollmentId)
      setEnrollments((prev) => prev.filter((e) => e.enrollmentId !== enrollmentId))
    } catch {
      alert('취소에 실패했습니다.')
    } finally {
      setSubmitting(null)
    }
  }

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-2">내 수강신청 목록</h1>
      <p className="text-sm text-gray-500 mb-6">
        전공: <span className="font-medium">{majorCredits}학점</span>
        {' · '}
        교양: <span className="font-medium">{liberalCredits}학점</span>
        {' · '}
        총 신청학점: <span className="font-medium">{totalCredits}학점</span>
      </p>

      {enrollments.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <p className="mb-4">수강신청한 강의가 없습니다.</p>
          <Link to="/courses" className="text-blue-600 hover:underline text-sm">
            강의 목록 보기
          </Link>
        </div>
      ) : (
        <div className="border rounded-lg divide-y">
          {enrollments.map((item) => (
            <div
              key={item.enrollmentId}
              className="flex items-center justify-between px-4 py-4 gap-4"
            >
              <div className="flex-1 min-w-0">
                <Link
                  to={`/courses/${item.courseId}`}
                  className="font-medium text-blue-600 hover:underline truncate block"
                >
                  {item.courseName}
                </Link>
                <p className="text-sm text-gray-500 mt-0.5">{item.credits}학점</p>
              </div>

              <Button
                size="sm"
                variant="outline"
                disabled={submitting === item.enrollmentId}
                onClick={() => handleCancel(item.enrollmentId)}
              >
                {submitting === item.enrollmentId ? '취소 중...' : '수강취소'}
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

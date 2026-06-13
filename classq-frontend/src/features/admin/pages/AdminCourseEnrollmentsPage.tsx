import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getAdminCourseEnrollments } from '../api/adminApi'
import type { AdminEnrollmentItem } from '../types/admin'

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function AdminCourseEnrollmentsPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const [enrollments, setEnrollments] = useState<AdminEnrollmentItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    if (!courseId) return
    getAdminCourseEnrollments(Number(courseId))
      .then(setEnrollments)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [courseId])

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>
  if (error) return <p className="text-center text-red-500 py-20">목록을 불러오지 못했습니다.</p>

  return (
    <div className="max-w-4xl mx-auto p-6">
      <Link to="/admin/courses" className="text-sm text-blue-600 hover:underline mb-4 inline-block">
        ← 강의 목록
      </Link>
      <h1 className="text-2xl font-bold mb-6">수강신청 현황</h1>

      <div className="border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left font-medium text-gray-600">학생</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">신청 일시</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">상태</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {enrollments.length === 0 ? (
              <tr>
                <td colSpan={3} className="px-4 py-10 text-center text-gray-400">
                  수강신청 내역이 없습니다.
                </td>
              </tr>
            ) : (
              enrollments.map((e) => (
                <tr key={e.enrollmentId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-gray-900">{e.studentName}</td>
                  <td className="px-4 py-3 text-gray-600">{formatDate(e.enrolledAt)}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`text-xs px-2 py-1 rounded-full ${
                        e.status === 'COMPLETED'
                          ? 'bg-green-100 text-green-700'
                          : 'bg-gray-100 text-gray-500'
                      }`}
                    >
                      {e.status === 'COMPLETED' ? '수강 중' : '취소'}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

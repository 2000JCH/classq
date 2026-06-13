import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAdminCourses, forceCloseCourse } from '../api/adminApi'
import { Button } from '@/components/ui/button'
import type { AdminCourse } from '../types/admin'

export default function AdminCoursesPage() {
  const [courses, setCourses] = useState<AdminCourse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    getAdminCourses()
      .then(setCourses)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [])

  async function handleClose(courseId: number) {
    setSubmitting(true)
    try {
      await forceCloseCourse(courseId)
      setCourses((prev) =>
        prev.map((c) => (c.id === courseId ? { ...c, status: 'CLOSED' as const } : c)),
      )
      setConfirmId(null)
    } catch {
      alert('폐강 처리에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>
  if (error) return <p className="text-center text-red-500 py-20">목록을 불러오지 못했습니다.</p>

  return (
    <div className="max-w-6xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">강의 관리</h1>

      <div className="border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left font-medium text-gray-600">강의명</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">교수</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">학과</th>
              <th className="px-4 py-3 text-right font-medium text-gray-600">학점</th>
              <th className="px-4 py-3 text-right font-medium text-gray-600">잔여석</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">상태</th>
              <th className="px-4 py-3 text-right font-medium text-gray-600">관리</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {courses.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-10 text-center text-gray-400">
                  강의가 없습니다.
                </td>
              </tr>
            ) : (
              courses.map((c) => (
                <tr key={c.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <div className="font-medium text-gray-900">{c.name}</div>
                    <div className="flex gap-2 mt-1">
                      <Link
                        to={`/admin/courses/${c.id}/enrollments`}
                        className="text-xs text-blue-600 hover:underline"
                      >
                        수강현황
                      </Link>
                      <Link
                        to={`/admin/courses/${c.id}/waitlists`}
                        className="text-xs text-blue-600 hover:underline"
                      >
                        대기자
                      </Link>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-gray-600">{c.professorName}</td>
                  <td className="px-4 py-3 text-gray-500">{c.departmentName ?? '교양'}</td>
                  <td className="px-4 py-3 text-right text-gray-600">{c.credits}</td>
                  <td className="px-4 py-3 text-right">
                    <span className={c.remainingCapacity === 0 ? 'text-red-500' : 'text-green-600'}>
                      {c.remainingCapacity}/{c.capacity}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`text-xs px-2 py-1 rounded-full ${
                        c.status === 'ACTIVE'
                          ? 'bg-green-100 text-green-700'
                          : 'bg-red-100 text-red-700'
                      }`}
                    >
                      {c.status === 'ACTIVE' ? '운영 중' : '폐강'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    {c.status === 'ACTIVE' &&
                      (confirmId === c.id ? (
                        <div className="flex justify-end gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => setConfirmId(null)}
                            disabled={submitting}
                          >
                            취소
                          </Button>
                          <Button
                            size="sm"
                            className="bg-red-600 hover:bg-red-700 text-white"
                            onClick={() => handleClose(c.id)}
                            disabled={submitting}
                          >
                            확인
                          </Button>
                        </div>
                      ) : (
                        <Button
                          size="sm"
                          variant="outline"
                          className="text-red-600 border-red-300 hover:bg-red-50"
                          onClick={() => setConfirmId(c.id)}
                        >
                          강제 폐강
                        </Button>
                      ))}
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

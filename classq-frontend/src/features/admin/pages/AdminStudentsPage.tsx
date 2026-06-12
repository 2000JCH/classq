import { useEffect, useState } from 'react'
import { getAdminStudents, forceDeleteStudent } from '../api/adminApi'
import { Button } from '@/components/ui/button'
import type { AdminStudent } from '../types/admin'

export default function AdminStudentsPage() {
  const [students, setStudents] = useState<AdminStudent[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    getAdminStudents()
      .then(setStudents)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [])

  async function handleDelete(studentId: number) {
    setSubmitting(true)
    try {
      await forceDeleteStudent(studentId)
      setStudents((prev) => prev.filter((s) => s.id !== studentId))
      setConfirmId(null)
    } catch {
      alert('탈퇴 처리에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>
  if (error) return <p className="text-center text-red-500 py-20">목록을 불러오지 못했습니다.</p>

  return (
    <div className="max-w-5xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">학생 관리</h1>

      <div className="border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left font-medium text-gray-600">이름</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">이메일</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">학과</th>
              <th className="px-4 py-3 text-right font-medium text-gray-600">학년</th>
              <th className="px-4 py-3 text-right font-medium text-gray-600">관리</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {students.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-10 text-center text-gray-400">
                  학생이 없습니다.
                </td>
              </tr>
            ) : (
              students.map((s) => (
                <tr key={s.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-gray-900">{s.name}</td>
                  <td className="px-4 py-3 text-gray-600">{s.email}</td>
                  <td className="px-4 py-3 text-gray-600">{s.departmentName}</td>
                  <td className="px-4 py-3 text-right text-gray-600">{s.grade}학년</td>
                  <td className="px-4 py-3 text-right">
                    {confirmId === s.id ? (
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
                          onClick={() => handleDelete(s.id)}
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
                        onClick={() => setConfirmId(s.id)}
                      >
                        강제 탈퇴
                      </Button>
                    )}
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

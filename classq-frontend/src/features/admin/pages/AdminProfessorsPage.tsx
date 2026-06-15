import { useEffect, useState } from 'react'
import { getPendingProfessors, approveProfessor } from '../api/adminApi'
import { Button } from '@/components/ui/button'
import type { PendingProfessor } from '../types/admin'

export default function AdminProfessorsPage() {
  const [professors, setProfessors] = useState<PendingProfessor[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [submitting, setSubmitting] = useState<number | null>(null)

  useEffect(() => {
    getPendingProfessors()
      .then(setProfessors)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [])

  async function handleApprove(accountId: number) {
    setSubmitting(accountId)
    try {
      await approveProfessor(accountId)
      setProfessors((prev) => prev.filter((p) => p.accountId !== accountId))
    } catch {
      alert('승인에 실패했습니다.')
    } finally {
      setSubmitting(null)
    }
  }

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>
  if (error) return <p className="text-center text-red-500 py-20">목록을 불러오지 못했습니다.</p>

  return (
    <div className="max-w-5xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">교수 승인 관리</h1>

      <div className="border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left font-medium text-gray-600">이름</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">이메일</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">학과</th>
              <th className="px-4 py-3 text-right font-medium text-gray-600">관리</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {professors.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-10 text-center text-gray-400">
                  승인 대기 중인 교수가 없습니다.
                </td>
              </tr>
            ) : (
              professors.map((p) => (
                <tr key={p.accountId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-gray-900">{p.name}</td>
                  <td className="px-4 py-3 text-gray-600">{p.email}</td>
                  <td className="px-4 py-3 text-gray-600">{p.departmentName}</td>
                  <td className="px-4 py-3 text-right">
                    <Button
                      size="sm"
                      onClick={() => handleApprove(p.accountId)}
                      disabled={submitting === p.accountId}
                    >
                      {submitting === p.accountId ? '처리 중...' : '승인'}
                    </Button>
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

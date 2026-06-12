import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getAdminCourseWaitlists } from '../api/adminApi'
import type { AdminWaitlistItem } from '../types/admin'

const STATUS_LABELS: Record<AdminWaitlistItem['status'], string> = {
  WAITING: '대기 중',
  NOTIFIED: '수락 대기',
  EXPIRED: '시간 초과',
  COMPLETED: '수락 완료',
}

const STATUS_COLORS: Record<AdminWaitlistItem['status'], string> = {
  WAITING: 'bg-gray-100 text-gray-600',
  NOTIFIED: 'bg-yellow-100 text-yellow-700',
  EXPIRED: 'bg-red-100 text-red-500',
  COMPLETED: 'bg-green-100 text-green-700',
}

export default function AdminCourseWaitlistsPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const [waitlists, setWaitlists] = useState<AdminWaitlistItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    if (!courseId) return
    getAdminCourseWaitlists(Number(courseId))
      .then(setWaitlists)
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
      <h1 className="text-2xl font-bold mb-6">대기자 명단</h1>

      <div className="border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-right font-medium text-gray-600">순번</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">학생</th>
              <th className="px-4 py-3 text-left font-medium text-gray-600">상태</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {waitlists.length === 0 ? (
              <tr>
                <td colSpan={3} className="px-4 py-10 text-center text-gray-400">
                  대기자가 없습니다.
                </td>
              </tr>
            ) : (
              waitlists.map((w) => (
                <tr key={w.waitlistId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-right text-gray-600">{w.rank}</td>
                  <td className="px-4 py-3 text-gray-900">{w.studentName}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs px-2 py-1 rounded-full ${STATUS_COLORS[w.status]}`}>
                      {STATUS_LABELS[w.status]}
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

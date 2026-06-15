import { useEffect, useState } from 'react'
import { getEnrollmentStats } from '../api/adminApi'
import type { EnrollmentStats } from '../types/admin'

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="border rounded-lg p-6 text-center">
      <p className="text-sm text-gray-500 mb-2">{label}</p>
      <p className="text-3xl font-bold text-gray-900">{value.toLocaleString()}</p>
    </div>
  )
}

export default function AdminStatsPage() {
  const [stats, setStats] = useState<EnrollmentStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    getEnrollmentStats()
      .then(setStats)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>
  if (error || !stats) return <p className="text-center text-red-500 py-20">통계를 불러오지 못했습니다.</p>

  return (
    <div className="max-w-2xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">수강신청 통계</h1>
      <div className="grid grid-cols-3 gap-4">
        <StatCard label="오늘 신청" value={stats.todayCount} />
        <StatCard label="전체 신청" value={stats.totalCount} />
        <StatCard label="취소" value={stats.cancelledCount} />
      </div>
    </div>
  )
}

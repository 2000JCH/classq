import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyWaitlists, acceptWaitlist, rejectWaitlist, cancelWaitlist } from '../api/enrollmentApi'
import { Button } from '@/components/ui/button'
import type { WaitlistItem, WaitlistStatus } from '../types/enrollment'

const STATUS_LABELS: Record<WaitlistStatus, string> = {
  WAITING: '대기 중',
  NOTIFIED: '수락 가능',
  EXPIRED: '시간 초과',
  COMPLETED: '수락 완료',
}

const STATUS_COLORS: Record<WaitlistStatus, string> = {
  WAITING: 'bg-gray-100 text-gray-600',
  NOTIFIED: 'bg-yellow-100 text-yellow-700',
  EXPIRED: 'bg-red-100 text-red-500',
  COMPLETED: 'bg-green-100 text-green-700',
}

export default function MyWaitlistsPage() {
  const [waitlists, setWaitlists] = useState<WaitlistItem[]>([])
  const [credits, setCredits] = useState({ current: 0, max: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [submitting, setSubmitting] = useState<number | null>(null)

  async function load() {
    try {
      const res = await getMyWaitlists()
      setWaitlists(res.waitlists)
      setCredits({ current: res.currentCredits, max: res.maxCredits })
    } catch {
      setError(true)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  async function handleAccept(waitlistId: number) {
    setSubmitting(waitlistId)
    try {
      await acceptWaitlist(waitlistId)
      await load()
    } catch {
      alert('수락에 실패했습니다.')
    } finally {
      setSubmitting(null)
    }
  }

  async function handleReject(waitlistId: number) {
    setSubmitting(waitlistId)
    try {
      await rejectWaitlist(waitlistId)
      await load()
    } catch {
      alert('거절에 실패했습니다.')
    } finally {
      setSubmitting(null)
    }
  }

  async function handleCancel(waitlistId: number) {
    setSubmitting(waitlistId)
    try {
      await cancelWaitlist(waitlistId)
      setWaitlists((prev) => prev.filter((w) => w.waitlistId !== waitlistId))
    } catch {
      alert('취소에 실패했습니다.')
    } finally {
      setSubmitting(null)
    }
  }

  if (loading) return <p className="text-center text-gray-500 py-20">로딩 중...</p>
  if (error) return <p className="text-center text-red-500 py-20">목록을 불러오지 못했습니다.</p>

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-2">내 대기 목록</h1>
      <p className="text-sm text-gray-500 mb-6">
        현재 학점:{' '}
        <span className={credits.current >= credits.max ? 'text-red-500 font-medium' : ''}>
          {credits.current}/{credits.max}학점
        </span>
      </p>

      {waitlists.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <p className="mb-4">대기 중인 강의가 없습니다.</p>
          <Link to="/courses" className="text-blue-600 hover:underline text-sm">
            강의 목록 보기
          </Link>
        </div>
      ) : (
        <div className="border rounded-lg divide-y">
          {waitlists.map((item) => (
            <div key={item.waitlistId} className="flex items-center justify-between px-4 py-4 gap-4">
              <div className="flex-1 min-w-0">
                <Link
                  to={`/courses/${item.courseId}`}
                  className="font-medium text-blue-600 hover:underline truncate block"
                >
                  {item.courseName}
                </Link>
                <p className="text-sm text-gray-500 mt-0.5">대기 순번 {item.rank}번</p>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <span className={`text-xs px-2 py-1 rounded-full ${STATUS_COLORS[item.status]}`}>
                  {STATUS_LABELS[item.status]}
                </span>

                {item.status === 'NOTIFIED' && (
                  <>
                    <Button
                      size="sm"
                      disabled={submitting === item.waitlistId}
                      onClick={() => handleAccept(item.waitlistId)}
                    >
                      수락
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={submitting === item.waitlistId}
                      onClick={() => handleReject(item.waitlistId)}
                    >
                      거절
                    </Button>
                  </>
                )}

                {item.status === 'WAITING' && (
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={submitting === item.waitlistId}
                    onClick={() => handleCancel(item.waitlistId)}
                  >
                    취소
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

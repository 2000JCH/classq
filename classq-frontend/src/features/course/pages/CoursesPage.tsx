import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getCourses, getDepartments } from '../api/courseApi'
import { useAuthStore } from '@/shared/store/authStore'
import type {
  ClassMode,
  ClassType,
  CourseFilters,
  CourseListItem,
  CourseType,
  Department,
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

export default function CoursesPage() {
  const role = useAuthStore((state) => state.role)
  const [courses, setCourses] = useState<CourseListItem[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [filters, setFilters] = useState<CourseFilters>({ page: 0, size: 20 })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(false)

  useEffect(() => {
    getDepartments().then(setDepartments).catch(() => {})
  }, [])

  useEffect(() => {
    setLoading(true)
    setError(false)
    getCourses(filters)
      .then((res) => {
        setCourses(res.content)
        setTotalPages(res.totalPages)
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [filters])

  function handleFilterChange(key: keyof CourseFilters, value: string | number | undefined) {
    setFilters((prev) => ({ ...prev, [key]: value || undefined, page: 0 }))
  }

  return (
    <div className="max-w-6xl mx-auto p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">강의 목록</h1>
        {role === 'PROFESSOR' && (
          <Link
            to="/professor/courses/new"
            className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
          >
            강의 등록
          </Link>
        )}
      </div>

      <div className="flex flex-wrap gap-3 mb-6">
        <select
          className="border rounded px-3 py-2 text-sm"
          defaultValue=""
          onChange={(e) => handleFilterChange('courseType', e.target.value as CourseType)}
        >
          <option value="">전체 강의 유형</option>
          <option value="MAJOR_REQUIRED">전공필수</option>
          <option value="MAJOR_ELECTIVE">전공선택</option>
          <option value="LIBERAL_ARTS">교양</option>
        </select>

        <select
          className="border rounded px-3 py-2 text-sm"
          defaultValue=""
          onChange={(e) => handleFilterChange('classType', e.target.value as ClassType)}
        >
          <option value="">전체 수업 유형</option>
          <option value="THEORY">이론</option>
          <option value="PRACTICE">실습</option>
        </select>

        <select
          className="border rounded px-3 py-2 text-sm"
          defaultValue=""
          onChange={(e) => handleFilterChange('classMode', e.target.value as ClassMode)}
        >
          <option value="">전체 수업 방식</option>
          <option value="ONLINE">온라인</option>
          <option value="OFFLINE">오프라인</option>
        </select>

        <select
          className="border rounded px-3 py-2 text-sm"
          defaultValue=""
          onChange={(e) =>
            handleFilterChange('departmentId', e.target.value ? Number(e.target.value) : undefined)
          }
        >
          <option value="">전체 학과</option>
          {departments.map((dept) => (
            <option key={dept.id} value={dept.id}>
              {dept.name}
            </option>
          ))}
        </select>
      </div>

      {loading ? (
        <p className="text-center text-gray-500 py-10">로딩 중...</p>
      ) : error ? (
        <p className="text-center text-red-500 py-10">강의 목록을 불러오지 못했습니다.</p>
      ) : (
        <>
          <div className="border rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">강의명</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">교수</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">학과</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">유형</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">방식</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">학점</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">잔여석</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">대기석</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">상태</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {courses.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="px-4 py-10 text-center text-gray-400">
                      강의가 없습니다.
                    </td>
                  </tr>
                ) : (
                  courses.map((course) => (
                    <tr key={course.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <Link
                          to={`/courses/${course.id}`}
                          className="text-blue-600 hover:underline font-medium"
                        >
                          {course.name}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-gray-700">{course.professorName}</td>
                      <td className="px-4 py-3 text-gray-500">
                        {course.departmentName ?? '교양'}
                      </td>
                      <td className="px-4 py-3 text-gray-700">
                        {COURSE_TYPE_LABELS[course.courseType]} /{' '}
                        {CLASS_TYPE_LABELS[course.classType]}
                      </td>
                      <td className="px-4 py-3 text-gray-700">
                        {CLASS_MODE_LABELS[course.classMode]}
                      </td>
                      <td className="px-4 py-3 text-right text-gray-700">{course.credits}</td>
                      <td className="px-4 py-3 text-right">
                        <span
                          className={
                            course.remainingCapacity === 0 ? 'text-red-500' : 'text-green-600'
                          }
                        >
                          {course.remainingCapacity}/{course.capacity}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        {course.waitlistLimit === 0 ? (
                          <span className="text-gray-400">-</span>
                        ) : (
                          <span
                            className={
                              course.remainingWaitlist === 0 ? 'text-red-500' : 'text-yellow-600'
                            }
                          >
                            {course.remainingWaitlist}/{course.waitlistLimit}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        {(() => {
                          const label =
                            course.status !== 'ACTIVE' ? '마감' :
                            course.remainingCapacity > 0 ? '신청 가능' :
                            course.waitlistLimit > 0 && course.remainingWaitlist > 0 ? '대기 가능' : '마감'
                          const color =
                            label === '신청 가능' ? 'bg-green-100 text-green-700' :
                            label === '대기 가능' ? 'bg-yellow-100 text-yellow-700' :
                            'bg-red-100 text-red-700'
                          return <span className={`text-xs px-2 py-1 rounded-full ${color}`}>{label}</span>
                        })()}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-4">
              {Array.from({ length: totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setFilters((prev) => ({ ...prev, page: i }))}
                  className={`px-3 py-1 rounded text-sm border ${
                    filters.page === i
                      ? 'bg-blue-600 text-white border-blue-600'
                      : 'text-gray-600 hover:bg-gray-100'
                  }`}
                >
                  {i + 1}
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
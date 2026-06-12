# ClassQ 프론트엔드 구현 진행 순서

---

## Phase 1. 프로젝트 초기화

- [x] Vite + React + TypeScript 프로젝트 생성 (`classq-frontend/`)
- [x] Tailwind CSS 설정
- [x] shadcn/ui 설치 및 초기화
- [x] React Router v7 설치
- [x] Zustand 설치
- [x] axios 설치
- [x] @microsoft/fetch-event-source 설치
- [x] 폴더 구조 생성 (`features/`, `shared/`, `app/`)

---

## Phase 2. 공통 기반

- [x] axios 인스턴스 생성 (`shared/api/axiosInstance.ts`)
- [x] 401 interceptor 구현 (silent refresh → 원래 요청 재시도 → 실패 시 로그인 이동)
- [x] Zustand store 구현 (`shared/store/authStore.ts`) — access token + role 저장, JWT 디코딩
- [x] React Router 라우트 정의 (`app/router.tsx`)
- [x] PrivateRoute 구현 — 비인증 시 로그인 페이지로 리다이렉트
- [x] RoleRoute 구현 — 역할(STUDENT/PROFESSOR/ADMIN) 불일치 시 접근 차단
- [x] 앱 진입 시 silent refresh (`App.tsx`) — access token 없으면 `/auth/refresh` 자동 호출

---

## Phase 3. 인증

- [x] 로그인 페이지 (`features/auth/pages/LoginPage.tsx`)
- [x] 회원가입 페이지 (`features/auth/pages/SignupPage.tsx`)
- [x] 로그아웃 처리 (Redis DEL + Cookie 삭제 + store 초기화 + 로그인 이동)

---

## Phase 4. 학생

- [x] 강의 목록 페이지 — courseType / classType / classMode / departmentId 필터
- [x] 강의 상세 페이지 — 강의 정보 + 시간표
- [x] 수강신청 — POST `/api/v1/enrollments`
- [x] 수강신청 취소 — DELETE `/api/v1/enrollments/{enrollmentId}`
- [x] 대기자 등록 — POST `/api/v1/waitlists`
- [x] 대기자 취소 — DELETE `/api/v1/waitlists/{waitlistId}`
- [x] 대기 수락 — POST `/api/v1/waitlists/{waitlistId}/accept`
- [x] 대기 거절 — POST `/api/v1/waitlists/{waitlistId}/reject`
- [ ] 내 수강신청 목록 페이지 — GET `/api/v1/enrollments/me`
- [x] 내 대기 목록 페이지 — GET `/api/v1/waitlists/me`
- [ ] 내 정보 조회/수정 페이지 — GET/PUT `/api/v1/students/me`
- [ ] 회원 탈퇴 — DELETE `/api/v1/students/me`

---

## Phase 5. 알림 (SSE)

- [ ] SSE 연결 훅 구현 (`features/notification/hooks/useSSE.ts`)
  - `@microsoft/fetch-event-source` 사용
  - `Authorization: Bearer {token}` 헤더 포함
  - 연결 끊김 시 자동 재연결
- [ ] 실시간 알림 수신 — WAITLIST_AVAILABLE / COURSE_CLOSED
- [ ] 알림 목록 페이지 — GET `/api/v1/notifications`
- [ ] 읽음 처리 — PATCH `/api/v1/notifications/{notificationId}/read`

---

## Phase 6. 교수

- [ ] 강의 등록 페이지 — POST `/api/v1/courses`
- [ ] 강의 수정 페이지 — PUT `/api/v1/courses/{courseId}`
- [ ] 강의 폐강 — DELETE `/api/v1/courses/{courseId}`
- [ ] 내 정보 조회/수정 페이지 — GET/PUT `/api/v1/professors/me`

---

## Phase 7. 관리자

- [ ] 학생 목록 페이지 — GET `/api/v1/admin/students`
- [ ] 학생 강제 탈퇴 — DELETE `/api/v1/admin/students/{studentId}`
- [ ] 강의 목록 페이지 — GET `/api/v1/admin/courses`
- [ ] 강의 강제 폐강 — DELETE `/api/v1/admin/courses/{courseId}`
- [ ] 수강신청 현황 페이지 — GET `/api/v1/admin/courses/{courseId}/enrollments`
- [ ] 대기자 명단 페이지 — GET `/api/v1/admin/courses/{courseId}/waitlists`
- [ ] 수강신청 통계 페이지 — GET `/api/v1/admin/stats/enrollments`
# ClassQ 구현 진행 순서

---

## Phase 1. 프로젝트 기반 설정

- [x] 패키지 구조 구성 (`domain`, `infra`, `api`, `global` 등)
- [x] `application.yml` 설정 (DB, Redis, Kafka, JWT)
- [ ] DB 스키마 생성 (9개 테이블)
  - account, student, professor, department
  - course, course_schedule
  - enrollment, waitlist, notification
- [x] JPA Entity 9개 작성
- [ ] Spring Security 기본 설정 (SecurityFilterChain)
- [ ] JWT 유틸 구현 (발급 / 검증 / 파싱)
- [ ] JWT 인증 필터 구현 (JwtAuthenticationFilter)
- [x] 공통 에러 응답 형식 구현 (`code`, `message`)
- [x] 글로벌 예외 핸들러 구현 (GlobalExceptionHandler)

---

## Phase 2. 인증 (Auth)

- [ ] POST `/api/v1/auth/signup` — 회원가입
- [ ] POST `/api/v1/auth/login` — 로그인 (access token + refresh token 발급, Redis SET)
- [ ] POST `/api/v1/auth/logout` — 로그아웃 (Redis refresh token DEL)
- [ ] POST `/api/v1/auth/refresh` — access token 재발급 (Redis GET으로 유효성 검증)

---

## Phase 3. 사용자 · 학과

- [ ] GET `/api/v1/students/me` — 내 정보 조회
- [ ] PUT `/api/v1/students/me` — 내 정보 수정
- [ ] DELETE `/api/v1/students/me` — 회원 탈퇴 (soft delete)
- [ ] GET `/api/v1/professors/me` — 내 정보 조회
- [ ] PUT `/api/v1/professors/me` — 내 정보 수정
- [ ] GET `/api/v1/departments` — 전체 학과 목록 조회

---

## Phase 4. 강의 (Course)

- [ ] GET `/api/v1/courses` — 강의 목록 조회 (courseType / classType / classMode / departmentId 필터)
- [ ] GET `/api/v1/courses/{courseId}` — 강의 상세 조회
- [ ] GET `/api/v1/courses/{courseId}/schedules` — 강의 시간표 조회
- [ ] POST `/api/v1/courses` — 강의 등록
  - course + course_schedule RDS INSERT
  - Redis `enrollment:course:{id}` = capacity 초기화
  - Redis `waitlist:course:{id}` = waitlist_limit 초기화
- [ ] PUT `/api/v1/courses/{courseId}` — 강의 수정 (Debezium이 감지하여 Kafka 발행)
- [ ] DELETE `/api/v1/courses/{courseId}` — 강의 폐강 (status = CLOSED)

---

## Phase 5. Kafka · Debezium 설정

- [ ] Kafka 토픽 생성
  - `enrollment-events` (파티션 3개)
  - `enrollment-cancel-events` (파티션 1개)
  - `course-events` (파티션 1개)
  - `enrollment-dead-letter` (파티션 1개)
- [ ] Kafka Producer 설정 (acks=all)
- [ ] Debezium CDC 설정 — course 테이블 변경 감지 → `course-events` 발행
- [ ] Course Consumer 구현
  - 정원 변경 시: RDS COUNT → 잔여 자리 재계산 후 Redis SET + 대기자 알림
  - 폐강 시: 수강/대기 학생 COURSE_CLOSED 알림 + 잠금 해제 + Redis key 삭제

---

## Phase 6. 수강신청 (Enrollment) ← 핵심

### 동기 구간
- [ ] POST `/api/v1/enrollments` — 수강신청
  1. `lock:course:{id}` 확인 → 있으면 "대기자 처리 중" 응답
  2. `schedule:student:{id}` 시간표 중복 체크 (캐시 없으면 RDS 조회 후 SET)
  3. `credits:student:{id}` 19학점 초과 체크 (캐시 없으면 RDS 조회 후 SET)
  4. `DECR enrollment:course:{id}` → 음수면 INCR 롤백 후 "마감됨" 응답
  5. Kafka `enrollment-events` 발행

### 비동기 구간
- [ ] Enrollment Consumer (`enrollment-processor`)
  - RDS INSERT (enrollment, status = COMPLETED)
  - Redis `schedule:student:{id}` 시간 추가
  - Redis `credits:student:{id}` INCRBY
  - 3회 재시도 실패 시 `enrollment-dead-letter` 전송

### 취소 동기 구간
- [ ] DELETE `/api/v1/enrollments/{enrollmentId}` — 수강신청 취소
  1. Redis `INCR enrollment:course:{id}`
  2. Redis `schedule:student:{id}` 해당 시간 삭제
  3. Redis `credits:student:{id}` DECRBY
  4. Kafka `enrollment-cancel-events` 발행

### 취소 비동기 구간
- [ ] Cancel Consumer
  - RDS UPDATE (enrollment status = CANCELLED)
  - 대기자 있으면: `SET lock:course:{id}` + waitlist rank 1번 NOTIFIED + expired_at 세팅 + notification INSERT

### 조회
- [ ] GET `/api/v1/enrollments/me` — 내 수강신청 목록 조회

---

## Phase 7. 대기자 (Waitlist)

- [ ] POST `/api/v1/waitlists` — 대기자 등록
  - `DECR waitlist:course:{id}` → 음수면 INCR 롤백 후 "대기 불가" 응답
  - RDS INSERT (waitlist, status = WAITING)
- [ ] DELETE `/api/v1/waitlists/{waitlistId}` — 대기자 취소
  - Redis `INCR waitlist:course:{id}`
  - RDS UPDATE (soft delete)
- [ ] GET `/api/v1/waitlists/me` — 내 대기 목록 조회 (currentCredits / maxCredits 포함)
- [ ] POST `/api/v1/waitlists/{waitlistId}/accept` — 대기 수락
  - 학점 초과 / 시간 중복 체크 → 실패 시 status = EXPIRED → 다음 순번으로
  - 성공 시: `DECR enrollment:course:{id}` + Kafka 발행 + `DEL lock:course:{id}`
- [ ] POST `/api/v1/waitlists/{waitlistId}/reject` — 대기 거절 → 다음 순번으로
- [ ] Scheduler (1분 주기) — expired_at 초과 NOTIFIED 대기자 처리
  - status = EXPIRED
  - 다음 순번 있으면: 잠금 유지 + 알림 발송
  - 다음 순번 없으면: `DEL lock:course:{id}`

---

## Phase 8. 알림 (Notification)

- [ ] GET `/api/v1/notifications` — 내 알림 목록 조회
- [ ] PATCH `/api/v1/notifications/{notificationId}/read` — 읽음 처리 (read_at 갱신)

---

## Phase 9. 관리자 (Admin)

- [ ] GET `/api/v1/admin/students` — 전체 학생 목록 조회
- [ ] DELETE `/api/v1/admin/students/{studentId}` — 특정 학생 강제 탈퇴
- [ ] GET `/api/v1/admin/courses` — 전체 강의 목록 조회
- [ ] DELETE `/api/v1/admin/courses/{courseId}` — 특정 강의 강제 폐강
- [ ] GET `/api/v1/admin/courses/{courseId}/enrollments` — 수강신청 현황 조회
- [ ] GET `/api/v1/admin/courses/{courseId}/waitlists` — 대기자 명단 조회
- [ ] GET `/api/v1/admin/stats/enrollments` — 수강신청 현황 통계

---

## Phase 10. 모니터링

- [ ] Prometheus 메트릭 설정 (API 응답시간, Kafka Consumer lag, RDS 커넥션 수)
- [ ] Grafana 대시보드 연동 (Prometheus + CloudWatch 통합)

---

## 구현 우선순위 요약

```
Phase 1 (기반) → Phase 2 (인증) → Phase 3 (사용자) → Phase 4 (강의)
→ Phase 5 (Kafka/Debezium) → Phase 6 (수강신청) → Phase 7 (대기자)
→ Phase 8 (알림) → Phase 9 (관리자) → Phase 10 (모니터링)
```

> Phase 6이 핵심이며 Phase 5 완료 후 진행해야 한다.
> Phase 7의 대기자 수락 흐름은 Phase 6의 수강신청 플로우를 재사용한다.

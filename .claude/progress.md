# ClassQ 구현 진행 순서

---

## Phase 1. 프로젝트 기반 설정

- [x] 패키지 구조 구성 (`domain`, `infra`, `api`, `global` 등)
- [x] `application.yml` 설정 (DB, Redis, Kafka, JWT)
- [x] DB 스키마 생성 (9개 테이블)
  - account, student, professor, department
  - course, course_schedule
  - enrollment, waitlist, notification
- [x] JPA Entity 9개 작성
- [x] Spring Security 기본 설정 (SecurityFilterChain)
- [x] JWT 유틸 구현 (발급 / 검증 / 파싱)
- [x] JWT 인증 필터 구현 (JwtFilter)
- [x] 공통 에러 응답 형식 구현 (`code`, `message`)
- [x] 글로벌 예외 핸들러 구현 (GlobalExceptionHandler)

---

## Phase 2. 인증 (Auth)

- [x] POST `/api/v1/auth/signup` — 회원가입
- [x] POST `/api/v1/auth/login` — 로그인 (access token + refresh token 발급, Redis SET)
- [ ] 토큰 저장 방식 변경 — refresh token을 httpOnly Cookie로 전달하도록 수정
  - 로그인: response body `{ accessToken }` + `Set-Cookie: refreshToken` (HttpOnly)
  - `/auth/refresh`: Authorization 헤더 → Cookie에서 refresh token 읽도록 변경
  - `/auth/logout`: Redis DEL + Cookie 삭제 (`Max-Age=0`)
- [x] POST `/api/v1/auth/logout` — 로그아웃 (Redis refresh token DEL)
- [x] POST `/api/v1/auth/refresh` — access token 재발급 (Redis GET으로 유효성 검증)
- [x] JWT 토큰 타입 클레임 추가 — access/refresh 토큰 구별 검증 (JwtUtil, JwtFilter, AccountService)

---

## Phase 3. 사용자 · 학과

- [x] GET `/api/v1/students/me` — 내 정보 조회
- [x] PUT `/api/v1/students/me` — 내 정보 수정
- [x] DELETE `/api/v1/students/me` — 회원 탈퇴 (soft delete)
- [x] GET `/api/v1/professors/me` — 내 정보 조회
- [x] PUT `/api/v1/professors/me` — 내 정보 수정
- [x] GET `/api/v1/departments` — 전체 학과 목록 조회

---

## Phase 4. 강의 (Course)

- [x] GET `/api/v1/courses` — 강의 목록 조회 (courseType / classType / classMode / departmentId 필터)
- [x] GET `/api/v1/courses/{courseId}` — 강의 상세 조회
- [x] GET `/api/v1/courses/{courseId}/schedules` — 강의 시간표 조회
- [x] POST `/api/v1/courses` — 강의 등록
  - course + course_schedule RDS INSERT
  - Redis `enrollment:course:{id}` = capacity 초기화
  - Redis `waitlist:course:{id}` = waitlist_limit 초기화
- [x] PUT `/api/v1/courses/{courseId}` — 강의 수정 (Debezium이 감지하여 Kafka 발행)
- [x] DELETE `/api/v1/courses/{courseId}` — 강의 폐강 (status = CLOSED)

---

## Phase 5. Kafka · Debezium 설정

- [x] Kafka 토픽 생성
  - `enrollment-events` (파티션 3개)
  - `enrollment-cancel-events` (파티션 1개)
  - `course-events` (파티션 1개)
  - `enrollment-dead-letter` (파티션 1개)
- [x] Kafka Producer 설정 (acks=all)
- [x] docker-compose.yml 작성 (Kafka KRaft + Kafka Connect/Debezium + MySQL + Redis)
- [x] Debezium CDC 설정 — course 테이블 변경 감지 → `course-events` 발행
  - `docker/debezium-connector.json` 작성 (snapshot.mode=schema_only, RegexRouter로 course-events 매핑)
- [x] Course Consumer 구현 (`domain/course/consumer/CourseEventConsumer.java`)
  - 정원 변경 시: RDS COUNT → 잔여 자리 재계산 후 Redis SET (대기자 알림은 Phase 8)
  - 폐강 시: 잠금 해제 + Redis key 삭제 (수강/대기 학생 알림은 Phase 8)

---

## Phase 6. 수강신청 (Enrollment) ← 핵심

### 동기 구간
- [x] POST `/api/v1/enrollments` — 수강신청
  1. `lock:course:{id}` 확인 → 있으면 "대기자 처리 중" 응답
  2. `schedule:student:{id}` 시간표 중복 체크 (캐시 없으면 RDS 조회 후 SET)
  3. `credits:student:{id}` 19학점 초과 체크 (캐시 없으면 RDS 조회 후 SET)
  4. `DECR enrollment:course:{id}` → 음수면 INCR 롤백 후 "마감됨" 응답
  5. Kafka `enrollment-events` 발행

### 비동기 구간
- [x] Enrollment Consumer (`enrollment-processor`)
  - RDS INSERT (enrollment, status = COMPLETED)
  - Redis `schedule:student:{id}` 시간 추가
  - Redis `credits:student:{id}` INCRBY
  - 3회 재시도 실패 시 `enrollment-dead-letter` 전송

### 취소 동기 구간
- [x] DELETE `/api/v1/enrollments/{enrollmentId}` — 수강신청 취소
  1. Redis `INCR enrollment:course:{id}`
  2. Redis `schedule:student:{id}` 해당 시간 삭제
  3. Redis `credits:student:{id}` DECRBY
  4. Kafka `enrollment-cancel-events` 발행

### 취소 비동기 구간
- [x] Cancel Consumer
  - RDS UPDATE (enrollment status = CANCELLED)
  - 대기자 있으면: `SET lock:course:{id}` (TTL 15분) + waitlist rank 1번 NOTIFIED + expired_at 세팅 + notification INSERT

### 조회
- [x] GET `/api/v1/enrollments/me` — 내 수강신청 목록 조회

---

## Phase 7. 대기자 (Waitlist)

- [x] POST `/api/v1/waitlists` — 대기자 등록
  - `DECR waitlist:course:{id}` → 음수면 INCR 롤백 후 "대기 불가" 응답
  - RDS INSERT (waitlist, status = WAITING)
  - ⚠️ 동시성 문제 (coderabbit Critical) — 추후 보완 필요
    - **문제**: `exists 체크 → count+1(rank 계산) → save` 3단계가 원자적이지 않음
    - **증상1 (중복 등록)**: 학생 A가 exists 체크 통과 직후, 같은 학생의 요청이 또 exists 체크를 통과하면 같은 학생이 두 번 대기 등록될 수 있음. 단, `(student_id, course_id)` unique constraint가 DB에 있어서 실제 INSERT 시 하나는 예외 발생 → 완전히 막히진 않음
    - **증상2 (rank 중복)**: 학생 A와 B가 동시에 count 조회 시 둘 다 같은 값을 읽어 같은 rank가 부여될 수 있음 (예: 둘 다 rank=3)
    - **해결 방향**: 분산 락(Redisson) 적용 또는 DB 시퀀스/AUTO_INCREMENT로 rank 관리. 비관적 락(PESSIMISTIC_WRITE) / 낙관적 락(@Version) 도입도 검토.
- [x] DELETE `/api/v1/waitlists/{waitlistId}` — 대기자 취소
  - Redis `INCR waitlist:course:{id}`
  - RDS UPDATE (soft delete)
- [x] GET `/api/v1/waitlists/me` — 내 대기 목록 조회 (currentCredits / maxCredits 포함)
- [x] POST `/api/v1/waitlists/{waitlistId}/accept` — 대기 수락
  - 학점 초과 / 시간 중복 체크 → 실패 시 status = EXPIRED → 다음 순번으로
  - 성공 시: `DECR enrollment:course:{id}` + Kafka 발행 + `DEL lock:course:{id}`
  - ✅ enroll() 진입 시 NOTIFIED 대기자 존재하면 차단 로직 추가 (race condition 보완 완료)
- [x] POST `/api/v1/waitlists/{waitlistId}/reject` — 대기 거절 → 다음 순번으로
- [x] Scheduler (1분 주기) — expired_at 초과 NOTIFIED 대기자 처리
  - status = EXPIRED
  - 다음 순번 있으면: 잠금 유지 + 알림 발송
  - 다음 순번 없으면: `DEL lock:course:{id}`
  - ✅ EnrollmentCancelConsumer 멱등성 보완 완료 (Kafka 재처리 시 중복 CANCELLED/NOTIFIED/Notification 방지)

### 보완 필요 항목
- [x] `enroll()` 진입 시 NOTIFIED 대기자 존재하면 차단
  - **문제**: 수강신청 취소 처리 시 Redis 슬롯 +1은 됐는데, lock을 걸기 직전 타이밍에 일반 수강신청이 그 슬롯을 낚아채는 문제
  - 대기자는 "자리 났으니 10분 내에 수락하세요" 알림을 받았는데 실제로는 자리가 없는 상태가 됨
  - **해결**: `enroll()` 진입 시 NOTIFIED 상태 대기자가 있으면 `ENROLLMENT_LOCKED` 거절
- [x] `EnrollmentCancelConsumer` 멱등성 보완
  - **문제**: Kafka는 같은 메시지를 두 번 이상 전달할 수 있음 (at-least-once). Consumer가 처리 중 crash 후 재기동되면 같은 메시지를 다시 처리해서 CANCELLED 중복, NOTIFIED/Notification 중복 생성 가능
  - **해결**: enrollment가 이미 CANCELLED이면 early return / 이미 NOTIFIED 대기자가 존재하면 알림 발송 블록 전체 skip
- [ ] `EnrollmentCancelConsumer` TOCTOU 경쟁 조건 보완 (coderabbit Major)
  - **문제**: 수강 중인 학생 A, B가 거의 동시에 수강신청을 취소하면 Consumer 2개가 동시에 처리됨. 둘 다 "NOTIFIED 대기자 없음"을 확인하고 둘 다 대기자 1순위 C한테 알림을 발송 → C가 알림을 2번 받고, 2순위 D는 알림을 못 받음
  - **해결**: 1순위 WAITING 대기자 조회 시 DB 락(`@Lock(LockModeType.PESSIMISTIC_WRITE)`) 적용 → Consumer A가 락을 잡는 동안 Consumer B는 대기 → A가 C에게 알림 발송 후 커밋 → B가 실행되면 C는 이미 NOTIFIED라 WAITING 대기자가 없음 → B는 D에게 알림 발송
- [x] 대기자 취소 동시성 보완 — 개인이 같은 요청을 동시에 2번 이상 발송했을 때 문제 (WaitlistService)
  - **문제**: 같은 사람이 취소 버튼을 동시에 두 번 누르면, 커밋 전이라 두 트랜잭션 모두 WAITING 상태를 읽고 통과함. 실제 취소건수는 1건인데 Redis 슬롯은 +2가 됨 → 없는 자리가 생겨버림
  - soft delete라 DB UNIQUE 제약으로도 보호 안 됨
  - **해결 방향**: `findByIdForUpdate` 비관적 락(`PESSIMISTIC_WRITE`) 적용
- [x] 대기자 등록 동시성 보완 — 여러 명이 동시에 대기 등록을 눌렀을 때 문제
  - **문제**: rank를 `현재 활성 대기자 수 + 1`로 계산하는데, 여러 명이 동시에 count를 조회하면 같은 값을 읽어 같은 rank가 두 명 이상에게 부여됨 (예: c, d 둘 다 rank=3)
  - **동작 방식**: 현재 활성 대기자 수 조회 + 1 = 내 rank
    - a(rank=1), b(rank=2) 있는 상태에서 c가 등록 → count = 2 → c의 rank = 3
    - 학생 c: count 조회 → 2 → rank=3 예정
    - 학생 d: count 조회 → 2 → rank=3 예정 (c가 아직 INSERT 전)
    - c: rank=3 INSERT, d: rank=3 INSERT → rank 3이 두 명
  - **해결 방향**: Redisson 분산 락으로 강의별 직렬화 또는 DB 시퀀스로 rank 관리

---

## Phase 8. 알림 (Notification)

- [x] GET `/api/v1/notifications` — 내 알림 목록 조회
- [x] PATCH `/api/v1/notifications/{notificationId}/read` — 읽음 처리 (read_at 갱신)
- [ ] GET `/api/v1/notifications/subscribe` — SSE 연결 (실시간 알림 Push)
  - 대기자 순번 알림(WAITLIST_AVAILABLE), 강의 폐강 알림(COURSE_CLOSED) 수신
  - 30초마다 heartbeat 전송 (연결 끊김 방지)
  - Polling 대비 RDS 부하 감소 (이벤트 발생 시에만 쿼리)
  - ⚠️ `markAsRead()` 멱등성 문제 (coderabbit Minor) — 보완 완료
    - **문제**: `readAt`을 null 체크 없이 매번 덮어써서 최초 읽음 시각이 유실됨
    - **해결**: `readAt == null`일 때만 갱신하도록 null 체크 추가
  - ⚠️ 소유권 검증 시 알림 존재 여부 노출 (coderabbit Major) — 보완 완료
    - **문제**: `findById` 후 소유자 불일치 시 `FORBIDDEN` 반환 → 타 사용자 알림 ID 존재 여부를 외부에서 추측 가능 (정보 노출)
    - **해결**: `findByIdAndStudentId`로 소유자 조건을 쿼리에 포함, 실패 시 `NOTIFICATION_NOT_FOUND` 단일 응답으로 통일

---

## Phase 9. 관리자 (Admin)

- [x] GET `/api/v1/admin/students` — 전체 학생 목록 조회
- [x] DELETE `/api/v1/admin/students/{studentId}` — 특정 학생 강제 탈퇴
- [x] GET `/api/v1/admin/courses` — 전체 강의 목록 조회
- [x] DELETE `/api/v1/admin/courses/{courseId}` — 특정 강의 강제 폐강
- [x] GET `/api/v1/admin/courses/{courseId}/enrollments` — 수강신청 현황 조회
- [x] GET `/api/v1/admin/courses/{courseId}/waitlists` — 대기자 명단 조회
- [x] GET `/api/v1/admin/stats/enrollments` — 수강신청 현황 통계

---

## Phase 10. 모니터링

- [x] `build.gradle` + `application.yml` — Actuator + Micrometer Prometheus 설정
- [x] `docker/prometheus.yml` 작성 — Spring Boot 앱 scrape 설정
- [x] `docker-compose.yml` 수정 — Prometheus + Grafana 서비스 추가
- [x] Grafana 대시보드 구성
  - API 엔드포인트별 응답시간 (P95 포함)
  - Kafka Consumer lag (`enrollment-processor` 그룹)
  - HikariCP 활성/대기 커넥션 수

### 논의 필요
- [ ] 설정 파일 디렉토리 구조 결정 — 현재 `docker/`에 Debezium, Prometheus 설정 혼재. `infra/`로 분리할지 `docker/`로 유지할지 팀 논의 필요
  - infra/ — Prometheus, Grafana, Kafka 설정 등 인프라 관련 파일을 통으로 묶는 방식. 규모가 커질수록 선호

---

## Phase 11. AWS 배포

- [ ] EKS + ECR — 앱 컨테이너 배포
- [ ] RDS (MySQL) 연결
- [ ] ElastiCache (Redis) 연결
- [ ] MSK 또는 EC2 Kafka + Debezium 연결
- [ ] EIP 설정 후 공유 (AWS/EC2 정적 IP — 팀원에게 공유)
- [ ] CloudWatch 연동
  - EKS Container Insights 활성화 (노드 CPU/메모리, Pod 상태)
  - RDS 슬로우 쿼리 / 커넥션 / CPU 메트릭
  - ElastiCache 메모리 / 커넥션 메트릭
  - MSK 사용 시 Kafka Consumer lag
  - Grafana에 CloudWatch 데이터 소스 추가

---

## Phase 12. 부하 테스트

- [ ] NGrinder 로컬 빌드 (네이버 부하 테스트 툴)
- [ ] 수강신청 폭주 시나리오 부하 테스트 실행
- [ ] Grafana로 병목 구간 확인 및 개선

---

## 구현 우선순위 요약

```
Phase 1 (기반) → Phase 2 (인증) → Phase 3 (사용자) → Phase 4 (강의)
→ Phase 5 (Kafka/Debezium) → Phase 6 (수강신청) → Phase 7 (대기자)
→ Phase 8 (알림) → Phase 9 (관리자) → Phase 10 (모니터링)
→ Phase 11 (AWS 배포) → Phase 12 (부하 테스트)
```

> Phase 6이 핵심이며 Phase 5 완료 후 진행해야 한다.
> Phase 7의 대기자 수락 흐름은 Phase 6의 수강신청 플로우를 재사용한다.
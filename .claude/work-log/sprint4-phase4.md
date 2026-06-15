# Sprint 4 — Phase 4. 강의 (Course)

**기간:** 2026-05-10 ~ 2026-05-15  
**상태:** ✅ 완료

---

## 목표

**배경**  
수강신청 전에 강의 정보가 먼저 DB와 Redis에 세팅되어야 한다. 강의 등록 시 `enrollment:course:{id}`, `waitlist:course:{id}` Redis 키를 초기화해야 수강신청 동기 구간이 올바르게 동작한다.

**문제 정의**  
강의 CRUD API와 Redis 초기화 로직이 없으면 이후 수강신청 Phase를 진행할 수 없다.

**대상 사용자/화면**  
교수(강의 등록/수정), 학생(강의 목록 탐색), 관리자(강의 관리)

**성공 기준 (완료 판정)**
- 기능: 강의 등록/조회/수정/폐강 API 정상 동작, 강의 등록 시 Redis 키 초기화 확인
- 성능: 해당 없음
- 정확도: 폐강 시 `status = CLOSED`, soft delete(`deleted_at`) 세팅
- 메모리: `enrollment:course:{id}` = capacity, `waitlist:course:{id}` = waitlist_limit 으로 초기화

---

## 스펙

**처리 대상**
- Course 엔티티: 강의 기본 정보
- CourseSchedule 엔티티: 강의 시간표 (Course 1 : N CourseSchedule)
- Redis: `enrollment:course:{id}`, `waitlist:course:{id}`

**제공 인터페이스**
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/v1/courses` | 인증 필요 | 강의 목록 조회 (필터: courseType / classType / classMode / departmentId) |
| GET | `/api/v1/courses/{courseId}` | 인증 필요 | 강의 상세 조회 |
| GET | `/api/v1/courses/{courseId}/schedules` | 인증 필요 | 강의 시간표 조회 |
| POST | `/api/v1/courses` | PROFESSOR / ADMIN | 강의 등록 + Redis 초기화 |
| PUT | `/api/v1/courses/{courseId}` | PROFESSOR / ADMIN | 강의 수정 |
| DELETE | `/api/v1/courses/{courseId}` | PROFESSOR / ADMIN | 강의 폐강 (status = CLOSED) |

**핵심 기능**
- 강의 목록 필터링 (courseType, classType, classMode, departmentId)
- 강의 등록 시 Redis `enrollment:course:{id}` = capacity, `waitlist:course:{id}` = waitlist_limit 초기화
- 폐강: soft delete + status CLOSED 처리

---

## 구현

### GET `/api/v1/courses` — 강의 목록 조회
- `CourseDto`: id, name, professorName, departmentName, courseType, classType, classMode, credits, capacity, minGrade, maxGrade, courseStatus
- `CourseService.getCourses()`: Specification으로 동적 필터 조합 + 페이징
- `Specification.where()` deprecated → `notDeleted().and(...)` 체이닝으로 변경

### GET `/api/v1/courses/{courseId}` — 강의 상세 조회
- `CourseDetailDto`: CourseDto 필드 + `waitlistLimit` 추가
- `CourseService.getCourseDetail()`: `findById` + `deletedAt` null 체크 → 없으면 `COURSE_NOT_FOUND(404)`
- `ErrorCode.COURSE_NOT_FOUND` 추가

### PUT `/api/v1/courses/{courseId}` — 강의 수정
- `CourseUpdateRequestDto`: 수정 가능 필드만 포함 (name, classMode, departmentId, capacity, waitlistLimit, minGrade, maxGrade)
- 수정 불가 필드: `credits`, `courseType`, `classType` (학생 데이터 영향), `schedules` (시간표 — 관리자만 변경 가능, Phase 9)
- `Course.update()`: 수정 가능 필드 7개 반영 메서드 추가
- `CourseService.updateCourse()`: 교수 조회 → 강의 조회 → 소유권 확인(`FORBIDDEN`) → 학과 조회 → `course.update()`
- `@Valid` + DTO 검증 애노테이션 추가 (`@NotBlank`, `@NotNull`, `@Min`)
- capacity/waitlistLimit: `int` → `Integer`로 변경 (미전송 시 0 흡수 방지)
- Redis(`enrollment:course:{id}`, `waitlist:course:{id}`) 갱신은 Phase 5 Debezium Consumer에 위임

### DELETE `/api/v1/courses/{courseId}` — 강의 폐강
- `Course.close()`: `courseStatus = CLOSED` 메서드 추가
- `CourseService.closeCourse()`: 교수 조회 → 강의 조회 → 소유권 확인(`FORBIDDEN`) → `course.close()`
- Redis 키 삭제 및 수강/대기 학생 알림은 Phase 5 Debezium Consumer에 위임

---

## 트러블슈팅

- `Specification.where()` Spring Data JPA 3.5.0에서 deprecated → `notDeleted().and(...)` 방식으로 교체

---

## 관련 문서
- `.claude/docs/api-design.md`
- `.claude/docs/redis-design.md`

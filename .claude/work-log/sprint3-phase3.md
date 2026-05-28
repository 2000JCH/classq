# Sprint 3 — Phase 3. 사용자 · 학과

**기간:** 2026-05-07 ~ 2026-05-08  
**상태:** ✅ 완료

---

## 목표

**배경**  
인증 이후 사용자가 본인의 정보를 조회·수정·탈퇴할 수 있어야 한다. 학과 목록은 수강신청 필터 등 다른 도메인에서도 참조하므로 먼저 제공해야 한다.

**문제 정의**  
현재 계정(account) 테이블만 존재하고, student / professor 프로필 정보와 학과 데이터를 외부에 노출하는 API가 없다.

**대상 사용자/화면**  
학생(마이페이지), 교수(프로필 페이지), 관리자(학과 관리)

**성공 기준 (완료 판정)**
- 기능: 학생/교수 본인 정보 조회·수정·탈퇴, 전체 학과 목록 조회 정상 동작
- 성능: 해당 없음
- 정확도: 탈퇴 시 `deleted_at` 세팅, 탈퇴 계정으로 로그인 시 401 반환
- 메모리: 해당 없음

---

## 스펙

**제공 인터페이스**
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/v1/students/me` | STUDENT | 내 정보 조회 |
| PUT | `/api/v1/students/me` | STUDENT | 내 정보 수정 |
| DELETE | `/api/v1/students/me` | STUDENT | 회원 탈퇴 (soft delete) |
| GET | `/api/v1/professors/me` | PROFESSOR | 내 정보 조회 |
| PUT | `/api/v1/professors/me` | PROFESSOR | 내 정보 수정 |
| GET | `/api/v1/departments` | 인증 필요 | 전체 학과 목록 조회 |

**핵심 기능**
- SecurityContext에서 accountId 추출 → 본인 정보만 조회/수정
- soft delete: 탈퇴 시 `account.deleted_at` + `student/professor.deleted_at` 세팅
- 탈퇴 계정 로그인 차단

---

## 구현

**StudentService / StudentController**  
`StudentRepository.findByAccountId()`로 본인 레코드를 조회한다. 응답 DTO(`StudentResponseDto`)에는 departmentId뿐 아니라 학과명(`departmentName`)을 함께 포함한다. 수정은 `StudentRequestDto`를 받아 `Student.update()` 도메인 메서드로 처리한다. 탈퇴는 `BaseEntity.delete()`로 `account.deleted_at`과 `student.deleted_at`을 동시에 세팅한다.

**ProfessorService / ProfessorController**  
학생과 동일한 구조로 구현한다. `ProfessorRepository.findByAccountId()`로 조회하고, `ProfessorRequestDto` → `Professor.update()` 흐름으로 수정한다. ErrorCode에 `PROFESSOR_NOT_FOUND`를 추가했다.

**DepartmentService / DepartmentController**  
`DepartmentRepository.findAll()`로 전체 학과를 조회해 `DepartmentDto` 리스트로 반환한다. 별도 페이징 없이 전체 목록을 내려준다.

**Phase 2 보완 (기간 내 포함)**  
- JwtUtil에 `tokenType` 클레임을 추가하고 JwtFilter에서 access 토큰 여부를 검증한다.
- `JWT_SECRET` 환경변수 기본값을 제거해 미설정 시 앱 시작 자체가 실패하도록 강화한다.
- CodeRabbit 리뷰 반영: `getTokenType()` 예외 처리 추가(500 → 401), `extractToken()` 메서드로 Bearer 추출 로직 통합, `UNAUTHORIZED`/`LOGIN_FAILED` ErrorCode 분리, `AccountService` 메서드에 `@Transactional` 명시.

---

## 트러블슈팅

**1. refresh 토큰으로 일반 API 호출이 허용되던 문제**
- 상황: JwtFilter가 토큰 서명·만료만 검증하고 토큰 종류를 구별하지 않아 refresh 토큰으로도 인증이 통과됐다.
- 원인: JWT 발급 시 `tokenType` 클레임을 포함하지 않았고, 필터에서도 해당 클레임을 검사하지 않았다.
- 해결: 발급 시 `tokenType: ACCESS / REFRESH` 클레임을 추가하고, JwtFilter에서 `ACCESS`가 아니면 401을 반환하도록 수정했다.

**2. JWT_SECRET 기본값으로 인한 보안 취약점**
- 상황: `application.yml`에 `${JWT_SECRET:default-secret}` 형태로 기본값이 설정되어 있어 환경변수 없이도 앱이 기동됐다.
- 원인: 로컬 개발 편의를 위해 임시로 넣은 기본값이 그대로 남아있었다.
- 해결: 기본값을 제거해 `JWT_SECRET` 미설정 시 앱 시작 단계에서 실패하도록 변경했다.

**3. 학생 정보 응답에 학과명 누락**
- 상황: `GET /api/v1/students/me` 응답에 `departmentId`만 포함되고 학과명이 빠져 있었다.
- 원인: DTO 설계 시 학과명 필드를 누락했고, Service에서 Department를 별도로 조회하는 로직이 없었다.
- 해결: `StudentService`에서 `DepartmentRepository`로 학과명을 추가 조회해 `StudentResponseDto`에 포함했다.

---

## 관련 문서
- `.claude/docs/api-design.md`
- `.claude/docs/db-design.md`

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 명령어

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun

# 전체 테스트
./gradlew test

# 단일 테스트 클래스
./gradlew test --tests "org.classq.domain.enrollment.EnrollmentServiceTest"

# 클린 빌드
./gradlew clean build
```

모든 명령어는 `classq-backend/` 디렉토리에서 실행한다.

## 아키텍처

ClassQ는 대학교 수강신청 시스템이다. 핵심 설계 목표는 수강신청 폭주 구간에서 RDS 부하를 제거하는 것이다. 학생이 응답을 기다리는 동기 구간은 Redis만 사용하고, RDS 쓰기는 Kafka Consumer를 통해 비동기로 처리한다.

### 패키지 구조

```
org.classq
├── domain/          # 도메인별 비즈니스 로직
│   ├── account/     # 인증 정보 (Account 엔티티, Role enum)
│   ├── student/
│   ├── professor/
│   ├── department/
│   ├── course/      # Course + CourseSchedule 엔티티, enum은 entity/enums/ 하위
│   ├── enrollment/
│   ├── waitlist/
│   └── notification/
├── global/
│   ├── entity/      # BaseTimeEntity (createdAt), BaseEntity (createdAt + deletedAt)
│   ├── auth/jwt/    # JWT 유틸, 필터 (미구현)
│   ├── config/      # Spring Security, Redis, Kafka 설정 (미구현)
│   └── exception/   # GlobalExceptionHandler, ErrorResponse (미구현)
└── api/             # 컨트롤러 (미구현)
```

각 도메인은 `entity/` → `repository/` → `service/` → `controller/` 레이어 순서로 구성한다.

### 엔티티 컨벤션

- `@NoArgsConstructor(access = PROTECTED)` — JPA 전용 생성자
- `@AllArgsConstructor(access = PRIVATE)` — Builder 전용 생성자
- 모든 연관관계는 `FetchType.LAZY`
- 모든 enum 컬럼에 `@Enumerated(EnumType.STRING)` — ORDINAL 절대 사용 금지
- `@Column(name = "status")`처럼 DB 컬럼명과 정확히 일치시킬 것 (`enrollment_status`처럼 임의로 붙이지 않음)
- NOT NULL 숫자 컬럼은 `int`, nullable 숫자 컬럼은 `Integer`
- TIME 타입 컬럼은 `LocalTime` (course_schedule), DATETIME은 `LocalDateTime`
- `CourseSchedule`은 베이스 엔티티 없음 — DB에 타임스탬프 컬럼이 없는 테이블
- Enrollment, Waitlist에는 `@Table(uniqueConstraints = ...)` 복합 유니크 키 적용

### 베이스 엔티티

`BaseTimeEntity` → `createdAt`만 보유 (적용 대상: department, course_schedule, notification)
`BaseEntity extends BaseTimeEntity` → `createdAt + deletedAt` (적용 대상: account, student, professor, course, enrollment, waitlist)

`@CreatedDate`가 동작하려면 `@EnableJpaAuditing`이 반드시 있어야 한다 — 메인 클래스 또는 `@Configuration` 클래스에 추가한다.

### 수강신청 핵심 플로우

**동기 구간 (Redis만 사용, 학생이 응답 대기):**
1. `lock:course:{id}` 확인 → 잠금 있으면 거절
2. `schedule:student:{id}` 시간표 중복 체크 (캐시 없으면 RDS 조회 후 저장)
3. `credits:student:{id}` 19학점 초과 체크 (캐시 없으면 RDS 조회 후 저장)
4. `DECR enrollment:course:{id}` → 음수면 `INCR` 롤백 후 거절
5. Kafka `enrollment-events` 발행 (acks=all)
6. 성공 응답 반환

**비동기 구간 (Kafka Consumer):**
- RDS enrollment 테이블 INSERT
- Redis `schedule:student:{id}`, `credits:student:{id}` 갱신
- 3회 재시도 실패 시 `enrollment-dead-letter` 전송

### Redis 키

| 키 | 초기화 시점 | TTL |
|---|---|---|
| `enrollment:course:{id}` | 강의 등록 (= capacity) | 없음 |
| `waitlist:course:{id}` | 강의 등록 (= waitlist_limit) | 없음 |
| `lock:course:{id}` | 대기자 처리 시작 시 | 없음 (Scheduler가 직접 해제) |
| `schedule:student:{id}` | 첫 수강신청 시 (RDS에서 로드) | 없음 |
| `credits:student:{id}` | 첫 수강신청 시 (RDS에서 로드) | 없음 |
| `refresh:token:{accountId}` | 로그인 시 | 7일 |

Redis는 AOF(`appendonly yes`) 방식으로 설정하여 장애 시 데이터를 복원한다.

### Kafka 토픽

| 토픽 | 파티션 수 | 발행 주체 |
|---|---|---|
| `enrollment-events` | 3 | 애플리케이션 |
| `enrollment-cancel-events` | 1 | 애플리케이션 |
| `course-events` | 1 | Debezium CDC |
| `enrollment-dead-letter` | 1 | Consumer 재시도 실패 |

Consumer 그룹: `enrollment-processor` / Producer: `acks=all` / Consumer: `enable-auto-commit=false`

### 에러 응답 형식

모든 에러는 아래 형식으로 반환한다.
```json
{ "code": "ENROLLMENT_CLOSED", "message": "수강 신청이 마감되었습니다." }
```

에러 코드: `ENROLLMENT_CLOSED`, `WAITLIST_CLOSED`, `ENROLLMENT_LOCKED`, `CREDIT_EXCEEDED`, `TIME_CONFLICT`, `DUPLICATE_ENROLLMENT`, `UNAUTHORIZED`, `FORBIDDEN`

### JWT

- 인증이 필요한 모든 요청에 `Authorization: Bearer {token}` 헤더 사용
- Access token: 30분 / Refresh token: 7일 (Redis에 `refresh:token:{accountId}`로 저장)
- 권한(Role): `STUDENT`, `PROFESSOR`, `ADMIN`
- 커스텀 프로퍼티: `jwt.secret`, `jwt.access-token-expiration`, `jwt.refresh-token-expiration`

### 도메인 규칙

- 학생 1인당 학기 최대 19학점
- 시간표 중복 판단: 일부라도 겹치면 거절 / 정확히 경계만 맞닿는 경우(09:00–11:00 / 11:00–13:00)는 통과
- Course의 `department_id` nullable — NULL이면 교양 강의 (학과 제한 없음)
- Course의 `min_grade` / `max_grade` nullable — NULL이면 학년 제한 없음
- `waitlist_limit = 0`이면 대기자 등록 불가
- 대기자 수락 제한 시간: 10분 (`expired_at = 현재시각 + 10분`)
- soft delete(`deleted_at`) 적용 대상: account, student, professor, course, enrollment, waitlist
- soft delete 미적용 대상: department, course_schedule, notification

### 환경 설정

`application.yml`에서 `${VAR:기본값}` 형식으로 환경변수를 읽는다. 운영 환경에서는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS`, `JWT_SECRET`을 설정해야 한다.

`jpa.hibernate.ddl-auto`는 로컬 개발 중 `create`, 운영에서는 `validate` 또는 `none`으로 변경한다.

## 구현 진행 상황

단계별 체크리스트는 `.claude/progress.md` 참고.

## 상세 설계 문서

이 파일에서 애매하거나 세부 내용이 필요한 경우 `.claude/docs/`의 문서를 확인한다.

| 파일 | 내용 |
|---|---|
| `architecture.md` | 전체 서비스 흐름, 동기/비동기 구간 상세, 대기자 처리 흐름, Kafka 구성, Redis 장애 대비 |
| `db-design.md` | 9개 테이블 DDL 전문, 각 테이블 설계 의도, 테이블 관계도, soft delete 적용 여부 기준 |
| `api-design.md` | 모든 엔드포인트 목록, 요청/응답 JSON 예시, 필터 파라미터, 에러 코드 전체 목록 |
| `redis-design.md` | Redis 키별 초기화/갱신/삭제 시점, AOF 설정 이유, 장애 복구 방법 |

## 협업 방식

코드 관련 질문을 받으면 코드를 직접 작성하거나 파일을 수정하지 않는다. 구현 방법과 이유를 설명하는 것에 그친다. 사용자가 명시적으로 "작성해줘", "수정해줘" 등의 지시를 할 때만 코드를 작성하거나 파일을 편집한다.
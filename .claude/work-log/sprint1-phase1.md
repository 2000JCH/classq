# Sprint 1 — Phase 1. 프로젝트 기반 설정

**기간:** 2026-05-01 ~ 2026-05-03  
**상태:** ✅ 완료

---

## 목표

**배경**  
ClassQ는 수강신청 폭주 구간에서 RDS 부하를 Redis + Kafka로 분산하는 구조가 핵심이다. 이를 위해 모든 도메인이 의존하는 공통 인프라(Entity, 예외 처리, 인증 필터, 환경 설정)를 먼저 구성해야 한다.

**문제 정의**  
아무 기반 없이 도메인별 기능을 만들면 컨벤션 불일치, 공통 에러 포맷 부재, Security 설정 충돌 등 후반 작업에서 수정 비용이 커진다.

**대상 사용자/화면**  
해당 없음 (인프라 설정 단계)

**성공 기준 (완료 판정)**
- 기능: 앱 정상 기동, 9개 Entity 테이블 생성 확인, `/api/v1/auth/**` 인증 없이 접근 가능
- 성능: 해당 없음
- 정확도: `@CreatedDate` 값 자동 저장, soft delete 컬럼(`deleted_at`) 정상 반영
- 메모리: 해당 없음

---

## 스펙

**제공 인터페이스**  
외부 API 없음. Spring Security 필터 체인과 공통 에러 응답 형식 제공.

**핵심 기능**
- 패키지 구조 구성 (`domain`, `global`)
- `application.yml` 환경변수 주입 설정 (DB, Redis, Kafka, JWT)
- 9개 Entity + JPA 매핑
- Spring Security `SecurityFilterChain` 기본 설정
- JWT 유틸(`JwtUtil`) + 인증 필터(`JwtFilter`) 뼈대
- 공통 에러 응답 형식 + `GlobalExceptionHandler`

---

## 구현

### 환경 설정
- Spring Boot 4.0.6 → 3.5.2 다운그레이드 (의존성 호환성 문제)
- `application.yml`: `${VAR:기본값}` 형식으로 DB, Redis, Kafka, JWT 값 주입

### Entity (9개)
- `account`, `student`, `professor`, `department`, `course`, `course_schedule`, `enrollment`, `waitlist`, `notification`
- 컨벤션: `@NoArgsConstructor(PROTECTED)` + `@AllArgsConstructor(PRIVATE)` + Builder
- 모든 연관관계 `FetchType.LAZY`, enum 컬럼 `@Enumerated(EnumType.STRING)`
- `BaseTimeEntity`(createdAt) / `BaseEntity`(createdAt + deletedAt) 계층 분리
- soft delete 적용: account, student, professor, course, enrollment, waitlist
- soft delete 미적용: department, course_schedule, notification

### 공통 예외 처리
- `ErrorCode` enum: HTTP 상태코드 + 메시지 한 곳에서 관리
- `BusinessException`: 비즈니스 규칙 위반 시 던지는 커스텀 예외
- `GlobalExceptionHandler`: `@RestControllerAdvice`로 전역 예외 캐치 → `{ code, message }` 형식 반환

### Spring Security + JWT
- `SecurityConfig`: `/api/v1/auth/**` 허용, 나머지 인증 필요
- `JwtUtil`: 토큰 발급 / 검증 / 파싱
- `JwtFilter`: 모든 요청에서 `Authorization: Bearer {token}` 헤더 추출 후 검증
- `JpaConfig`: `@EnableJpaAuditing` 설정 → `@CreatedDate` 동작 활성화

---

## 트러블슈팅

**1. Spring Boot 버전 호환성**
- 상황: 4.0.6으로 시작했으나 의존성 충돌 발생
- 원인: Spring Boot 4.x는 아직 일부 라이브러리와 호환되지 않음
- 해결: 3.5.2로 다운그레이드

**2. `@CreatedDate` 동작 안 함**
- 상황: `BaseTimeEntity`에 `@CreatedDate`를 붙였는데 값이 저장되지 않음
- 원인: `@EnableJpaAuditing`이 없으면 Auditing 기능이 비활성화됨
- 해결: `JpaConfig.java`에 `@EnableJpaAuditing` 추가

**3. Waitlist `rank` 컬럼명 예약어 충돌**
- 상황: JPA가 `rank`를 SQL 예약어로 인식해서 쿼리 생성 시 오류 발생
- 원인: `@Column(name = "rank")`로 명시해도 DB 방언에 따라 예약어 처리가 달라짐
- 해결: 컬럼명을 `waitlist_rank`로 변경

**4. jjwt 의존성 누락**
- 상황: JWT 관련 코드를 작성했으나 컴파일 오류
- 원인: `build.gradle`에 jjwt 의존성이 없었음
- 해결: `build.gradle`에 jjwt 의존성 추가

---

## 관련 문서
- `.claude/docs/db-design.md`
- `.claude/docs/architecture.md`
- `.claude/flows/JwtFilter.md`
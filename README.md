# ClassQ

> 대학교 수강신청 폭주 상황에서 발생하는 RDS 부하 문제를 해결하기 위한 백엔드 시스템

---

## 프로젝트 목적

수강신청 폭주 구간에서 RDS가 병목이 되는 문제를 해결한다.  
Redis로 잔여 자리를 빠르게 확보하고, Kafka로 후속 DB 처리를 비동기화하여  
**동기 구간에서 RDS를 완전히 제거**하는 것이 핵심 목표다.

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Database | MySQL (RDS) |
| Cache | Redis (로컬) / ElastiCache (AWS 배포) |
| Message Broker | Apache Kafka |
| CDC | Debezium |
| Security | Spring Security + JWT |
| Infra | AWS EKS, ECR, RDS, Terraform |
| Monitoring | Prometheus + CloudWatch + Grafana |
| Load Testing | Gatling |
| CI/CD | GitHub Actions + ECR |

---

## 핵심 아키텍처

수강신청 요청은 동기 구간과 비동기 구간으로 분리된다.

**동기 구간** — 학생이 응답을 기다리는 구간으로 RDS 조회 없이 Redis만 사용한다.
1. 잠금 확인 (lock:course:{id})
2. 시간표 중복 체크 (schedule:student:{id})
3. 학점 초과 체크 (credits:student:{id})
4. 잔여 자리 차감 (DECR enrollment:course:{id})
5. Kafka 이벤트 발행 후 즉시 응답

**비동기 구간** — Kafka Consumer가 RDS INSERT 및 Redis 캐시 갱신을 처리한다.

---

## 도메인 구조

```
org.classq
├── domain/
│   ├── account/      # 인증 정보 (Account 엔티티, Role enum)
│   ├── student/      # 학생
│   ├── professor/    # 교수
│   ├── department/   # 학과
│   ├── course/       # 강의
│   ├── enrollment/   # 수강신청
│   ├── waitlist/     # 대기자
│   └── notification/ # 알림
└── global/
    ├── auth/jwt/     # JWT 유틸, 필터
    ├── config/       # Spring Security, Redis, Kafka 설정
    └── exception/    # GlobalExceptionHandler, ErrorResponse
```

---

## ERD

> 테이블: account, student, professor, department, course, course_schedule, enrollment, waitlist, notification (총 9개)

```
account ──── student ──── enrollment ──── course ──── course_schedule
               │               │                │
               └── professor   └── waitlist     └── department

student    ──── department
professor  ──── department
course     ──── department

notification → student
notification → course
```

---

## Redis Key 설계

| Key | 용도 | TTL |
|---|---|---|
| `enrollment:course:{id}` | 잔여 수강 자리 | 없음 |
| `waitlist:course:{id}` | 잔여 대기 자리 | 없음 |
| `lock:course:{id}` | 대기자 처리 중 잠금 | 없음 |
| `schedule:student:{id}` | 학생 시간표 캐시 | 없음 |
| `credits:student:{id}` | 학생 총 학점 캐시 | 없음 |
| `refresh:token:{accountId}` | Refresh Token | 7일 |

---

## Kafka 토픽

| 토픽 | 용도 | 파티션 |
|---|---|---|
| enrollment-events | 수강신청 이벤트 | 3 |
| enrollment-cancel-events | 수강신청 취소 이벤트 | 1 |
| waitlist-promote-events | 대기자 순번 프로모션 (순서 보장) | 1 |
| course-events | 강의 변경/폐강 이벤트 (Debezium CDC) | 1 |
| enrollment-dead-letter | 처리 실패 이벤트 | 1 |

---

## 로컬 실행 방법

```bash
# 1. 인프라 실행 (MySQL, Redis, Kafka, Debezium, Prometheus, Grafana)
docker-compose up -d

# 2. Debezium 커넥터 등록 (수동 — 컨테이너 기동 후 한 번만 실행)
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @docker/debezium-connector.json

# 3. 백엔드 실행 (classq-backend/ 디렉토리에서 실행)
cd classq-backend
./gradlew bootRun

# 4. 프론트엔드 실행 (classq-frontend/ 디렉토리에서 실행)
cd classq-frontend
npm install
npm run dev
```

> `DataInitializer`가 앱 기동 시 테스트 계정(학생 6명, 교수 1명), 강의 7개, Redis 키를 자동으로 초기화한다.

| 서비스 | 주소 |
|---|---|
| 프론트엔드 | http://localhost:5173 |
| 백엔드 API | http://localhost:8080 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
| Debezium | http://localhost:8083 |

---

## 부하 테스트

Gatling으로 수강신청 동시 요청 시뮬레이션을 진행한다.  
Docker 및 백엔드 서버 실행 후 아래 명령어로 실행한다.

```bash
# classq-backend/ 디렉토리에서 실행
./gradlew gatlingRun
```

### 시나리오 1 — 수강신청 전체 플로우 (300명 동시, 로컬 Docker)

로그인 → 강의목록조회 → 수강신청 → 내수강신청조회 → 취소 → 재신청

| 항목 | Before (DB 직접 조회) | After (Redis-only) | 개선 |
|---|---|---|---|
| 평균 응답시간 | 1832ms | 1053ms | -43% |
| P95 | 4636ms | 2845ms | -39% |
| 처리량 | 109 req/s | 156 req/s | +43% |
| HikariCP 대기 | 180개 | ~0개 | — |
| 에러율 | 0% | 0% | — |

> 개선 원인: `enroll()` 동기 구간 DB 쿼리 4개 → Redis 캐시 조회로 대체. HikariCP 커넥션 풀 압박 해소.

### 시나리오 2 — 대기자 전체 플로우 (300명 동시, 대기 슬롯 30개)

로그인 → 대기 등록 → 취소 → 재등록

| 항목 | Before (DB) | After 최종 (Kafka 단일 파티션) |
|---|---|---|
| P95 | 3266ms | 3503ms |
| 처리량 | 142 req/s | 148 req/s |
| 에러율 | 2.51% | 0% |

> 에러 원인 변천: DB 데드락 → cancel() 데드락 심화 → Redis Sorted Set 도입으로 데드락 구조 제거 → Kafka 단일 파티션으로 순서 보장 이관.

### 시나리오 3 — 스트레스 테스트 (5,100명 점진적 주입, c6i.large 기준)

| 항목 | Before (BCrypt cost 10) | After (BCrypt cost 8 + 최적화) |
|---|---|---|
| P95 | 49,010ms | **21ms** |
| 처리량 | 93.85 req/s | **175.86 req/s** |
| 에러 | 9건 | 0건 |

> 핵심 병목: BCrypt cost 10. cost 8 채택 후 P95 49초 → 21ms (-99.95%), 처리량 +87%.  
> 적용 최적화: BCrypt cost 10→8 / HikariCP 10→20 / Kafka Consumer concurrency 1→3

---

## API 요약

| 도메인 | 주요 엔드포인트 |
|---|---|
| 인증 | POST /api/v1/auth/signup, /login, /logout, /refresh |
| 학생 | GET·PUT·DELETE /api/v1/students/me |
| 교수 | GET·PUT /api/v1/professors/me |
| 학과 | GET /api/v1/departments |
| 강의 | GET·POST·PUT·DELETE /api/v1/courses |
| 수강신청 | POST·DELETE·GET /api/v1/enrollments |
| 대기자 | POST·DELETE·GET /api/v1/waitlists, /accept, /reject |
| 알림 | GET /api/v1/notifications, PATCH /.../read |
| 관리자 | GET·DELETE /api/v1/admin/students, /admin/courses, /admin/stats/enrollments |
| 관리자 (계정) | GET /api/v1/admin/accounts/pending, PATCH /admin/accounts/{id}/approve |

---

## 문서

| 문서 | 설명 |
|---|---|
| [architecture.md](./.claude/docs/architecture.md) | 전체 서비스 아키텍처 |
| [db-design.md](./.claude/docs/db-design.md) | RDS 테이블 설계 |
| [api-design.md](./.claude/docs/api-design.md) | API 명세 |
| [redis-design.md](./.claude/docs/redis-design.md) | Redis Key 설계 |
| [frontend-design.md](./.claude/docs/frontend-design.md) | 프론트엔드 설계 |
| [test-data.md](./.claude/docs/test-data.md) | 로컬 테스트 계정 및 Gatling 부하 테스트 계정 |
| [load-test.md](./.claude/docs/load-test.md) | Gatling 부하 테스트 시나리오별 측정 결과, Before/After 비교 |

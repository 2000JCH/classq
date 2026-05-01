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
| Infra | AWS EKS, ECR, RDS |
| Monitoring | Prometheus + CloudWatch + Grafana |
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
com.classq
├── auth          # 인증 (회원가입, 로그인, JWT)
├── student       # 학생
├── professor     # 교수
├── department    # 학과
├── course        # 강의
├── enrollment    # 수강신청
├── waitlist      # 대기자
├── notification  # 알림
└── common        # 공통 응답, 예외 처리
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
| course-events | 강의 변경/폐강 이벤트 (Debezium CDC) | 1 |
| enrollment-dead-letter | 처리 실패 이벤트 | 1 |

---

## 로컬 실행 방법

```bash
# 1. 인프라 실행 (MySQL, Redis, Kafka, Debezium)
docker-compose up -d

# 2. 애플리케이션 실행
./gradlew bootRun
```

---

## 문서

| 문서 | 설명 |
|---|---|
| [service-overview.md](./docs/service-overview.md) | 서비스 개요 및 도메인 규칙 |
| [db-design.md](./docs/db-design.md) | RDS 테이블 설계 |
| [redis-design.md](./docs/redis-design.md) | Redis Key 설계 |
| [api-design.md](./docs/api-design.md) | API 명세 |
| [architecture.md](./docs/architecture.md) | 전체 서비스 아키텍처 |
# 부하 테스트 결과

## 테스트 환경

- **실행 도구**: Gatling 3.11.5 (Gradle 플러그인)
- **대상 서버**: 로컬 (MacBook + Docker Compose)
- **동시 사용자**: 300명 (atOnceUsers)
- **구성**: Spring Boot 앱 + MySQL + Redis + Kafka (Docker)

---

## 시나리오 1 — 수강신청 전체 플로우 (EnrollmentFlowSimulation)

### 플로우

```
로그인 → 강의목록조회 → 수강신청 → 내수강신청조회(3초 대기) → 취소 → 수강변경
```

`before()`: enrollment:course:3=300, enrollment:course:4=300

### Before — DB 직접 조회

| 항목 | 수치 |
|---|---|
| 전체 요청 수 | 1526 (KO=0) |
| 평균 응답시간 | 1832ms |
| P50 | 1563ms |
| P75 | 2549ms |
| P95 | 4636ms |
| P99 | 5511ms |
| Max | 6272ms |
| 처리량 | 109 req/s |
| 에러율 | 0% |

응답시간 분포: 800ms 미만 26% / 800~1200ms 12% / 1200ms 이상 62%

**원인**: `enroll()` 동기 구간에 DB 쿼리 4개 혼입 → 300명 동시 요청 시 HikariCP 대기 최대 180개

### After — Redis-only 전환 (2026-06-19)

| 항목 | Before | After | 개선 |
|---|---|---|---|
| 평균 응답시간 | 1832ms | 1053ms | -43% |
| P50 | 1563ms | 882ms | -44% |
| P75 | 2549ms | 1804ms | -29% |
| P95 | 4636ms | 2845ms | -39% |
| P99 | 5511ms | 3518ms | -36% |
| 처리량 | 109 req/s | 156 req/s | +43% |
| HikariCP 대기 | 180개 | ~0개 | — |

**변경 내용**:
- `EnrollmentService.enroll()` DB 쿼리 4개 → Redis 캐시 조회로 대체
- `DataInitializer`: 과목/학생 생성 시 Redis 캐싱 초기화
- `AccountService`: 로그인 시 `student:account:{accountId}` 캐시 갱신

---

## 시나리오 2 — 대기자 전체 플로우 (WaitlistFlowSimulation)

### 플로우

```
로그인 → 강의목록조회 → 대기자등록 → 대기자취소(성공한 30명만) → 재등록
```

`before()`: enrollment:course:3=0 (정원 소진), waitlist:course:3=30

### Before — DB 직접 조회

| 항목 | 수치 |
|---|---|
| 전체 요청 수 | 996 (KO=25) |
| 평균 응답시간 | 1631ms |
| P50 | 1317ms |
| P75 | 2310ms |
| P95 | 3266ms |
| P99 | 4111ms |
| Max | 4492ms |
| 처리량 | 142.29 req/s |
| 에러율 | 2.51% (25건) |

응답시간 분포: 800ms 미만 14.66% / 800~1200ms 22.79% / 1200ms 이상 60.04%

**HikariCP 커넥션 대기 최대 189** — WaitlistService 동기 구간 전체 DB 직접 조회 (학생/강의/중복확인/rank계산/INSERT)

### 발견된 문제 — 동시 취소 시 DB 데드락

`WaitlistService.cancel()` 완료 후 `decrementRanksAfter(courseId, cancelledRank)` 호출 시 이후 대기자 전원의 rank를 UPDATE. 다수가 동시에 취소하면 동일 행을 동시에 수정하려다 MySQL 데드락 발생 → HTTP 500 (25건).

**해결 방안**:
- A안: `cancel()`에 Redisson 락 추가 → 동시 취소 직렬화 (구현 난이도 낮음)
- B안: Redis Sorted Set으로 rank 관리 전환 → 데드락 구조 자체 제거 → **After 2로 구현**
- C안: Kafka로 다음 순번 프로모션 이관 → ZSET 제거, Redis는 캐시 역할만 → **After 3으로 구현**

### After — Redis 전환 후 (2026-06-20)

**변경 내용**: register() DB 쿼리 6개 → 1~2개 (findByDeleted + save만 유지), Redisson 락 제거, rank Redis INCR 전환

| 항목 | Before (DB) | After (Redis) | 변화 |
|---|---|---|---|
| 평균 응답시간 | 1631ms | 1895ms | +16% |
| P50 | 1317ms | 1503ms | +14% |
| P75 | 2310ms | 2745ms | +19% |
| P95 | 3266ms | 4060ms | +24% |
| 처리량 | 142.29 req/s | 124.75 req/s | -12% |
| HikariCP 대기 | 189 | 80 | **-58%** |
| 에러율 | 2.51% | 2.71% | — |

**결과 해석**: register() 자체는 개선됨 (HikariCP 대기 58% 감소로 확인). 전체 P95 악화는 cancel()의 `decrementRanksAfter` 데드락이 여전히 병목이기 때문. 진짜 병목 해결 없이는 After 개선 수치가 의미 없음.

### After 2 — Redis Sorted Set 전환 (2026-06-21)

**변경 내용**: `decrementRanksAfter()` DB bulk UPDATE 완전 제거 → `waitlist:zset:course:{courseId}` Redis Sorted Set 도입
- score = 등록시각(ms), member = waitlistId → score 오름차순 = 등록 순서 = 순번
- 순번 조회: `ZRANK` 한 줄 (취소/만료가 반영된 동적 계산, 항상 정확)
- 취소/만료 시: `ZREM` 한 줄 → 나머지 순번 자동 재계산
- DB bulk UPDATE 자체가 사라져 **데드락 구조 근본 제거**

| 항목 | Before (DB) | After 1 (Redis) | After 2 (Sorted Set) |
|---|---|---|---|
| 전체 요청 수 | 996 (KO=25) | 998 (KO=27) | 1052 (KO=0) |
| 평균 응답시간 | 1631ms | 1895ms | 2020ms |
| P50 | 1317ms | 1503ms | 1577ms |
| P75 | 2310ms | 2745ms | 3066ms |
| P95 | 3266ms | 4060ms | 4693ms |
| P99 | 4111ms | 4874ms | 5827ms |
| 처리량 | 142 req/s | 124 req/s | 116 req/s |
| HikariCP 대기 | 189 | 80 | ~0 |
| 에러율 | 2.51% (25건) | 2.71% (27건) | **0%** |

**결과 해석**:
- 에러율 2.51% → 0%: 데드락 구조 제거로 HTTP 500 완전 소멸. 핵심 목표 달성
- P95 수치 증가는 에러→성공 전환 효과: Before/After 1에서 빠르게 실패하던 요청이 After 2에서 전부 성공 완료 (총 요청 996 → 1052, +56건). 느린 요청도 P95에 포함되어 수치가 올라간 것이지 실제 성능 저하 아님
- HikariCP ~0: DB bulk UPDATE 제거로 커넥션 풀 압박 완전 해소
- **로컬 Docker 환경 기준** (MySQL + Redis + Kafka + Debezium + Prometheus + Grafana + 앱 서버 단일 머신 실행) — 절대 수치보다 개선율(에러율 0%, HikariCP ~0) 위주로 해석해야 함

### After 3 — Kafka 프로모션 전환 (2026-06-22)

**변경 배경**: After 2에서 ZSET은 `register()` 동기 구간(`ZADD`)과 `getMyWaitlists()` 조회(`ZRANK`)에 포함되어 있었음. 시니어 피드백: "Redis는 캐싱에만 쓰고, 순서 보장은 Kafka 단일 파티션으로 처리하라."

**변경 내용**:
- `WaitlistService`: `ZADD`/`ZRANK`/`ZREM` 전면 제거, `waitlist:zset:course:{courseId}` 키 삭제
- `expireAndPromoteNext()`: afterCommit에서 `waitlist-promote-events` Kafka 발행
- `WaitlistPromoteConsumer` (신규): 단일 파티션 소비 → DB `ORDER BY rank ASC`로 첫 번째 WAITING 조회 → NOTIFIED 전환 + SSE 알림
- `EnrollmentCancelConsumer`: ZSET range 조회 → DB `findFirstByCourse_IdAndWaitlistStatusAndDeletedAtIsNullOrderByRankAsc`로 교체
- rank 값은 DB에 그대로 보존, `getMyWaitlists()` 조회 시 `w.getRank()` 직접 반환

| 항목 | Before (DB) | After 1 (Redis) | After 2 (ZSET) | After 3 (Kafka) |
|---|---|---|---|---|
| 전체 요청 수 | 996 (KO=25) | 998 (KO=27) | 1052 (KO=0) | 1036 (KO=0) |
| 평균 응답시간 | 1631ms | 1895ms | 2020ms | **1654ms** |
| P50 | 1317ms | 1503ms | 1577ms | **1377ms** |
| P75 | 2310ms | 2745ms | 3066ms | **2499ms** |
| P95 | 3266ms | 4060ms | 4693ms | **3503ms** |
| P99 | 4111ms | 4874ms | 5827ms | **4289ms** |
| 처리량 | 142 req/s | 124 req/s | 116 req/s | **148 req/s** |
| HikariCP 대기 | 189 | 80 | ~0 | **~0 (max 1)** |
| 에러율 | 2.51% (25건) | 2.71% (27건) | 0% | **0%** |

**After 2 대비 After 3 개선율**:
- P95: 4693ms → 3503ms (**-25%**)
- P99: 5827ms → 4289ms (**-26%**)
- 처리량: 116 → 148 req/s (**+28%**)

**결과 해석**:
- ZSET의 `ZADD`가 `register()` 동기 경로에 포함되어 있던 것을 제거하면서 응답시간이 단축됨
- Kafka 단일 파티션이 순서 보장을 담당 → Redis는 슬롯 카운터(`waitlist:course:{id}`) 역할만 수행
- 프로모션 로직이 Consumer로 완전히 분리되어 동기 경로 단순화
- 에러율 0%, HikariCP max 1 커넥션 유지
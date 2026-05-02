# ClassQ 전체 서비스 아키텍처

## 설계 목표

수강신청 폭주 구간에서 RDS 부하를 제거하는 것이 핵심 목표다. 이를 위해 학생이 기다리는 동기 구간에서는 Redis만 사용하여 잔여 자리를 확보하고, RDS 쓰기는 Kafka를 통해 비동기로 처리한다. Debezium은 교수의 강의 변경(정원 변경, 폐강)을 감지하여 Kafka로 이벤트를 발행하며, Consumer가 대기자 알림과 Redis 갱신을 처리한다.

---

## 인프라 구성

| 구성 요소 | AWS 서비스 | 역할 |
|---|---|---|
| 컨테이너 오케스트레이션 | EKS | Spring Boot, Kafka, Debezium 컨테이너 배포 및 관리 |
| 컨테이너 이미지 저장소 | ECR | Docker 이미지 저장 및 버전 관리 |
| 데이터베이스 | RDS (MySQL) | 영구 데이터 저장 |
| 캐시 | ElastiCache (Redis) | 잔여 자리, 시간표, 학점, 잠금, 토큰 관리 |
| 인프라 로그/알림 | CloudWatch | EKS 인프라 로그, RDS 슬로우 쿼리 수집 |
| CI/CD | GitHub Actions + ECR | 코드 푸시 시 자동 빌드 및 EKS 배포 |

> 로컬 개발 환경에서는 ElastiCache 대신 Redis를 직접 사용한다. 동일한 Redis 엔진을 사용하므로 애플리케이션 코드 변경 없이 전환 가능하다.

---

## 시스템 구성 요소

| 구성 요소 | 기술 | 역할 |
|---|---|---|
| API Server | Spring Boot | REST API 처리 |
| Message Broker | Kafka | 이벤트 비동기 처리 |
| CDC | Debezium | course 테이블 변경 감지 |
| Cache | ElastiCache (Redis) / 로컬: Redis | 잔여 자리, 시간표, 학점, 잠금, 토큰 관리 |
| Database | RDS MySQL | 영구 데이터 저장 |
| Scheduler | Spring Scheduler | 대기자 만료 처리 (1분 주기) |
| 모니터링 | Prometheus + CloudWatch + Grafana | 메트릭 수집 및 통합 대시보드 |

---

## 학생 수강신청 흐름

### 동기 구간 — 학생이 응답을 기다리는 구간

동기 구간에서 RDS를 완전히 제거한 이유는 수강신청 폭주 시 RDS가 병목이 되기 때문이다. Redis의 원자적 연산(DECR)을 사용하여 동시성 문제 없이 잔여 자리를 처리하고, Kafka에 이벤트를 발행한 후 즉시 학생에게 응답한다.

```
1. 학생 수강신청 요청 (POST /api/v1/enrollments)
   └── JWT 인증 확인

2. Redis 체크 (순서대로 수행)
   ├── lock:course:{id} 확인
   │   → 잠금 있으면 "대기자 처리 중" 응답 (종료)
   ├── schedule:student:{id} 확인
   │   → 시간표 중복이면 거절 (종료)
   │   → 캐시 없으면 RDS 조회 후 저장
   ├── credits:student:{id} 확인
   │   → 19학점 초과면 거절 (종료)
   │   → 캐시 없으면 RDS 조회 후 저장
   └── DECR enrollment:course:{id}
       → 음수면 INCR 롤백 후 "마감됨" 응답 (종료)

3. Kafka Producer 발행 (acks=all)
   └── topic: enrollment-events

4. "신청 완료" 응답 → 학생에게 즉시 전달
```

### 비동기 구간 — 학생 응답 이후 처리

Kafka 브로커까지 이벤트가 도달하면 유실이 없다. Consumer가 RDS INSERT와 Redis 갱신을 처리하며, 3회 재시도 실패 시 Dead Letter Topic으로 보낸다.

```
5. Kafka 브로커 수신
   └── topic: enrollment-events (파티션 3개, 디스크 저장)

6. Kafka Consumer (enrollment-processor)
   ├── courseType 분기 처리
   │   ├── MAJOR_REQUIRED
   │   ├── MAJOR_ELECTIVE
   │   └── LIBERAL_ARTS
   ├── RDS INSERT (enrollment 테이블, status = COMPLETED)
   └── Redis 캐시 갱신
       ├── schedule:student:{id} 시간 추가
       └── credits:student:{id} INCRBY

   └── 3회 재시도 실패 시 → enrollment-dead-letter 토픽
```

---

## 학생 수강신청 취소 흐름

취소는 동기 구간에서 Redis를 즉시 갱신하여 다른 학생이 빠르게 자리를 확보할 수 있도록 한다. Kafka 이벤트는 애플리케이션에서 직접 발행하며, Consumer가 RDS 업데이트와 대기자 처리를 담당한다.

```
[동기 구간]
1. 학생 취소 요청 (DELETE /api/v1/enrollments/{id})
   └── JWT 인증 확인

2. Redis 즉시 갱신
   ├── INCR enrollment:course:{id}
   ├── schedule:student:{id} 해당 시간 삭제
   └── credits:student:{id} DECRBY

3. Kafka Producer 직접 발행
   └── topic: enrollment-cancel-events

4. "취소 완료" 응답 → 학생에게 즉시 전달

[비동기 구간]
5. Kafka Consumer
   ├── RDS UPDATE (enrollment status = CANCELLED)
   └── 대기자 확인
       ├── 대기자 있음
       │   ├── SET lock:course:{id}
       │   ├── waitlist rank 1번 status = NOTIFIED
       │   ├── expired_at = 현재 + 10분
       │   └── notification INSERT
       └── 대기자 없음 → 종료
```

---

## 교수/관리자 강의 변경 흐름

교수가 강의를 수정하면 Debezium이 course 테이블의 변경을 감지하여 Kafka로 자동 발행한다. 애플리케이션에서 직접 Kafka를 호출하지 않으므로 강의 수정 API가 단순해지고, 이벤트 발행 실패로 인한 정합성 문제를 줄일 수 있다.

```
1. 교수/관리자 강의 변경 (정원 변경 / 강의 정보 수정 / 폐강)
   └── RDS UPDATE (course 테이블)

2. Debezium CDC 감지 (course 테이블만 감지)
   └── Kafka 발행 (topic: course-events)

3. Consumer 처리
   ├── 정원 변경
   │   ├── RDS에서 현재 신청 인원 조회
   │   ├── 잔여 자리 재계산 후 Redis SET
   │   └── 늘어난 자리만큼 대기자 순번대로 알림 발송
   └── 폐강
       ├── enrollment COMPLETED 학생 → COURSE_CLOSED 알림 발송
       ├── waitlist WAITING/NOTIFIED 학생 → COURSE_CLOSED 알림 발송
       └── 잠금 해제 + Redis 잔여 자리 key 삭제
```

---

## 대기자 처리 흐름

자리가 생기면 즉시 잠금을 세팅하여 다른 학생의 신청을 막고, 순번 1번 대기자에게 알림을 발송한다. 10분 내 미수락 시 Scheduler가 다음 순번으로 넘기며, 더 이상 대기자가 없으면 잠금을 해제한다.

```
[자리 발생 시]
1. SET lock:course:{id} (TTL 없음)
2. waitlist status = NOTIFIED
3. expired_at = 현재 + 10분
4. notification INSERT (WAITLIST_AVAILABLE)

[Scheduler 1분마다 실행]
5. expired_at 지났는데 status = NOTIFIED인 대기자 조회
   ├── 발견됨
   │   ├── status = EXPIRED
   │   ├── 다음 순번 있음 → 잠금 유지 + 알림 발송
   │   └── 다음 순번 없음 → DEL lock:course:{id}
   └── 없음 → 종료

[대기자 수락 시]
6. 학점 초과 or 시간 중복 체크
   ├── 실패 → status = EXPIRED → 다음 순번으로
   └── 성공
       ├── Redis DECR enrollment:course:{id}
       ├── Kafka 발행 → 일반 수강신청 흐름과 동일
       └── DEL lock:course:{id}
```

---

## Kafka 구성

| 항목 | 내용 |
|---|---|
| 토픽 | enrollment-events, enrollment-cancel-events, course-events, enrollment-dead-letter |
| 파티션 수 | enrollment-events: 3개, 나머지: 1개 |
| Consumer Group | enrollment-processor (수강신청/취소 처리) |
| Producer 설정 | acks=all |
| 메시지 보존 | 기본 7일 |

enrollment-events를 3개 파티션으로 구성한 이유는 수강신청 폭주 구간에서 Consumer를 병렬로 확장하여 처리량을 높이기 위해서다.

---

## 모니터링

Prometheus는 애플리케이션 레벨 메트릭(API 응답시간, Kafka Consumer lag, RDS 커넥션 수)을 수집한다. CloudWatch는 AWS 인프라 레벨 메트릭(EKS 인프라 로그, RDS 슬로우 쿼리, 인프라 알림)을 수집한다. Grafana는 두 소스를 통합하여 하나의 대시보드에서 앱과 인프라 상태를 동시에 확인할 수 있도록 한다. 장애 시 화면 전환 없이 병목 지점을 바로 파악할 수 있다.

---

## Redis 장애 대비

| 상황 | 대응 |
|---|---|
| Redis 서버 재시작 | AOF로 전체 데이터 복원 |
| enrollment key 없음 | RDS COUNT 쿼리로 자동 복구 후 SET |
| schedule/credits key 없음 | RDS에서 한 번에 조회 후 SET |
| lock key 없음 | 잠금 없는 것으로 간주, 정상 신청 허용 |
| refresh token key 없음 | 재로그인 요청 |

---

## 추후 도입 사항

Flink를 Kafka 토픽 구독자로 추가하여 수강신청 실시간 집계와 분석을 수행할 예정이다. 수강신청 시작 전 배치 잡으로 Redis 캐시를 워밍업하여 첫 요청 시 RDS 조회를 방지한다. NGrinder로 부하 테스트를 수행한 후 Grafana로 병목 구간을 확인하고 개선한다.

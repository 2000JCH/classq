# ClassQ AWS 인스턴스 스펙 분석

> 로컬 부하 테스트(P95 2845ms, 156 req/s, 300명 동시)를 기준으로 AWS 환경에서의 스펙을 분석한다.
> 실제 측정은 Phase 12 AWS 배포 후 동일한 Gatling 시나리오로 진행한다.

---

## A. EC2 인스턴스 계열 특성

| 계열 | 타입 | CPU 방식 | 특징 | 약점 | 대표 인스턴스 |
|---|---|---|---|---|---|
| t | 버스터블 | 크레딧 기반 | 평소 절전, 크레딧 쌓아두다가 순간 burst | 지속 부하 시 크레딧 고갈 → 성능 반토막 | t3.medium, t3.large |
| c | 컴퓨트 최적화 | 상시 full CPU | CPU 성능 항상 일정, 네트워크 대역폭 넓음 | 메모리가 같은 크기 대비 상대적으로 적음 | c5.large, c5.xlarge |
| r | 메모리 최적화 | 상시 full CPU | RAM 대용량, 메모리 집약 워크로드 최적 | CPU는 m 계열과 비슷한 수준 | r5.large, r5.xlarge |

### EC2 인스턴스 실제 스펙 (AWS 공식)

| 인스턴스 | 계열 | vCPU | RAM (GiB) | 네트워크 | 세대 |
|---|---|---|---|---|---|
| t3.small | 버스터블 | 2 | 2 | Up to 5 Gbps | 3세대 |
| t3.medium | 버스터블 | 2 | 4 | Up to 5 Gbps | 3세대 |
| t3.large | 버스터블 | 2 | 8 | Up to 5 Gbps | 3세대 |
| c5.large | 컴퓨트 | 2 | 4 | Up to 10 Gbps | 5세대 |
| c5.xlarge | 컴퓨트 | 4 | 8 | Up to 10 Gbps | 5세대 |
| c5.2xlarge | 컴퓨트 | 8 | 16 | Up to 10 Gbps | 5세대 |
| c6i.large | 컴퓨트 | 2 | 4 | Up to 12.5 Gbps | 6세대 (현 권장) |
| c6i.xlarge | 컴퓨트 | 4 | 8 | Up to 12.5 Gbps | 6세대 (현 권장) |
| c6i.2xlarge | 컴퓨트 | 8 | 16 | Up to 12.5 Gbps | 6세대 (현 권장) |
| r5.large | 메모리 | 2 | 16 | Up to 10 Gbps | 5세대 |
| r5.xlarge | 메모리 | 4 | 32 | Up to 10 Gbps | 5세대 |

---

## B. ClassQ 컴포넌트별 스펙 분석 및 권장 사항

### B-1. 앱 서버 (Spring Boot on EKS)

**부하 특성 분석:**
- 로그인 시 BCrypt 해싱 → CPU 집약 (요청 1건당 약 100~300ms CPU 점유)
- 수강신청 동기 구간: Redis 조회 + Kafka 발행 → 네트워크 위주, CPU 부하 낮음
- Kafka Consumer (`enrollment-processor`): 3 파티션 병렬 소비 → 추가 CPU 소모
- JVM heap: 최소 512MB, 안정 운영 시 1~2GB 권장

**스펙별 비교:**

| 인스턴스 | vCPU | RAM | 수강신청 폭주 대응 | 판단 |
|---|---|---|---|---|
| t3.medium | 2 | 4 GiB | 크레딧 방식 — 300명 로그인 집중 시 BCrypt 처리로 크레딧 소진 → 이후 vCPU 20% 수준으로 제한 | ❌ 부적합 |
| c6i.large | 2 | 4 GiB | CPU 항상 full 사용 가능. 수강신청 폭주 구간 안정적이나 vCPU 2개 한계 | △ 최소 사양 |
| c6i.xlarge | 4 | 8 GiB | vCPU 4개로 BCrypt 병렬 처리 + Kafka Consumer 3 파티션 동시 소비 여유. 네트워크 12.5 Gbps로 c5 대비 개선 | ✅ 권장 |
| c6i.2xlarge | 8 | 16 GiB | ClassQ 300명 규모에서 과잉. 트래픽 증가 시 scale-up 후보 | — 확장용 |

**권장: `c6i.xlarge` (4 vCPU / 8 GiB)**
> t 계열은 수강신청 폭주(= 지속적인 CPU 사용) 구간에 크레딧 고갈로 성능이 급락하므로 제외. c6i는 c5의 현 권장 후속 세대로 동일 스펙에 네트워크 12.5 Gbps(c5 대비 +25%)로 개선됐다.

---

### B-2. RDS (MySQL)

**부하 특성 분석:**
- 수강신청 동기 구간은 Redis-only → RDS 직접 쿼리 없음
- Kafka Consumer가 비동기로 enrollment INSERT → 순간 spike 가능
- InnoDB 버퍼 풀 크기 = 총 RAM의 70~80% 권장. 버퍼 풀이 클수록 디스크 I/O 감소

**RDS 전용 인스턴스 스펙 (AWS 공식):**

| 인스턴스 | vCPU | RAM (GiB) | InnoDB 버퍼 풀 (70%) | 네트워크 |
|---|---|---|---|---|
| db.t3.medium | 2 | 4 | ~2.8 GiB | 5 Gbps (burst) |
| db.t3.large | 2 | 8 | ~5.6 GiB | 5 Gbps (burst) |
| db.r5.large | 2 | 16 | ~11.2 GiB | Up to 10 Gbps |
| db.r5.xlarge | 4 | 32 | ~22.4 GiB | Up to 10 Gbps |

**스펙별 비교:**

| 인스턴스 | 버퍼 풀 | 판단 |
|---|---|---|
| db.t3.medium | ~2.8 GiB | enrollment/waitlist/course 인덱스 전부 올리기 빠듯. Consumer spike 시 디스크 I/O 발생 | ❌ 부적합 |
| db.t3.large | ~5.6 GiB | 인덱스 일부 상주 가능하나 burst 방식이라 지속 INSERT 시 성능 불안정 | △ 개발용 |
| db.r5.large | ~11.2 GiB | 9개 테이블 인덱스 + 활성 행 전부 메모리 상주 가능. Consumer 비동기 INSERT spike 흡수 | ✅ 권장 |
| db.r5.xlarge | ~22.4 GiB | 300명 규모 과잉. 데이터 누적 후 scale-up 후보 | — 확장용 |

**권장: `db.r5.large` (2 vCPU / 16 GiB)**
> RDS는 InnoDB 버퍼 풀 크기가 핵심이다. db.t3 계열은 RAM 부족으로 잦은 디스크 I/O가 발생하고, burst 방식이라 Kafka Consumer가 몰릴 때 성능이 불안정하다. db.r5.large는 버퍼 풀 11 GiB로 인덱스 전체를 메모리에 올려 쿼리 안정성을 확보한다.

---

### B-3. ElastiCache (Redis)

**부하 특성 분석:**
- ClassQ Redis 저장 데이터 추산:
  - `enrollment:course:{id}` / `waitlist:course:{id}`: 7개 강의 × ~50B = ~700B
  - `schedule:student:{id}`: 학생 300명 × 평균 시간표 5개 × ~100B = ~150KB
  - `credits:student:{id}`: 300명 × ~50B = ~15KB
  - `student:account:{id}`: 300명 × ~50B = ~15KB
  - `course:{id}:schedules` / `:credits` / `:name`: 7개 × ~200B = ~1.4KB
  - `refresh:token:{id}`, `lock:course:{id}` 등 소량
  - **총합 약 1 GiB 미만** (학생 수 10배 증가 시에도 수십 MB 수준)
- AOF(appendonly yes) 설정으로 write 시 디스크 동기화 → 네트워크 + I/O 부하 추가

**ElastiCache 전용 인스턴스 스펙 (AWS 공식):**

| 인스턴스 | vCPU | RAM (GiB) | 기본 대역폭 | Burst 대역폭 |
|---|---|---|---|---|
| cache.t3.medium | 2 | 3.09 | 0.256 Gbps | 5 Gbps |
| cache.r5.large | 2 | 13.07 | 0.75 Gbps | 10 Gbps |
| cache.r6g.large | 2 | 13.07 | 0.75 Gbps | 10 Gbps |
| cache.r6g.xlarge | 4 | 26.04 | 1.25 Gbps | 10 Gbps |

**스펙별 비교:**

| 인스턴스 | RAM | 기본 대역폭 | 판단 |
|---|---|---|---|
| cache.t3.medium | 3.09 GiB | 0.256 Gbps | 데이터 용량은 충분하나, 수강신청 폭주 시 DECR/GET 명령 폭주 + AOF 쓰기로 0.256 Gbps 기본 대역폭 병목 위험 | △ 개발용 |
| cache.r6g.large | 13.07 GiB | 0.75 Gbps | 데이터 대비 메모리 여유 충분. 기본 대역폭 0.75 Gbps로 수강신청 폭주 + AOF 동시 처리 안정. 최신 Graviton2 기반 | ✅ 권장 |
| cache.r6g.xlarge | 26.04 GiB | 1.25 Gbps | 300명 규모 과잉 | — 확장용 |

**권장: `cache.r6g.large` (2 vCPU / 13.07 GiB)**
> 데이터 용량은 1 GiB 미만이지만 cache.t3.medium은 기본 대역폭 0.256 Gbps로 폭주 시 네트워크 병목이 발생한다. cache.r6g.large는 기본 0.75 Gbps + burst 10 Gbps로 안정적이며 AOF 설정에서도 여유가 있다.

---

### B-4. MSK (Kafka)

**부하 특성 분석:**
- 300명 동시 수강신청 → 최대 300 msg/s (`enrollment-events` 3파티션)
- Consumer `enrollment-processor` 3 파티션 병렬 소비
- Debezium CDC (`course-events`) 상시 연결
- 메시지 직렬화/역직렬화 + 파티션 복제(기본 replication factor 3) → CPU 위주 부하

**MSK 지원 브로커 크기 (AWS 공식):**

- Standard: `kafka.t3.small` / `kafka.m5.large~24xlarge` / `kafka.m7g.large~16xlarge`
- Express: `express.m7g.large~16xlarge`

> ⚠️ AWS MSK 공식 페이지에 브로커별 vCPU/RAM 수치가 명시되어 있지 않음. "EC2 General Purpose Instances 페이지를 참고하라"고만 안내함. 아래 스펙은 EC2 m5/t3 계열 기준으로 참고.

| 인스턴스 | vCPU (EC2 기준) | RAM (EC2 기준) | AWS 용도 분류 |
|---|---|---|---|
| kafka.t3.small | 2 | 2 GiB | 개발/테스트 전용 (크레딧 방식) |
| kafka.m5.large | 2 | 8 GiB | 소규모 프로덕션 (브로커당 최대 1000 파티션) |
| kafka.m5.xlarge | 4 | 16 GiB | 중규모 프로덕션 (브로커당 최대 1000 파티션) |
| kafka.m7g.large | 2 | 8 GiB | 소규모 프로덕션 — m5 대비 비용 효율 개선, Graviton 기반 |

**스펙별 비교:**

| 인스턴스 | 판단 |
|---|---|
| kafka.t3.small | AWS가 "개발/테스트 전용"으로 명시. 크레딧 방식으로 지속 부하 부적합 | ❌ 부적합 |
| kafka.m5.large | ClassQ 5개 토픽 × 최대 3파티션 = 15파티션으로 1000파티션 한도 내 여유. 300 msg/s + Debezium 처리 가능 | ✅ 권장 |
| kafka.m7g.large | m5.large와 동일 스펙이나 Graviton 기반으로 비용 효율 개선. 리전 지원 여부 확인 필요 | ✅ 대안 (비용 최적화) |

**권장: `kafka.m5.large` × 3 브로커 (MSK 최소 구성)**
> AWS 권장 최소 구성은 3 AZ × 브로커 1개씩 = 3브로커. replication factor 3 충족. ClassQ 5개 토픽 15파티션은 브로커당 1000파티션 한도 대비 여유롭다.

---

## C. 컴포넌트별 권장 스펙 요약

| 컴포넌트 | 권장 인스턴스 | vCPU | RAM | 선택 이유 |
|---|---|---|---|---|
| 앱 서버 (EKS) | `c6i.xlarge` | 4 | 8 GiB | BCrypt CPU 집약 + Kafka Consumer 병렬 처리. c5 후속 세대로 네트워크 12.5 Gbps 개선 |
| RDS (MySQL) | `db.r5.large` | 2 | 16 GiB | InnoDB 버퍼 풀 11 GiB 확보로 인덱스 전체 메모리 상주 |
| ElastiCache (Redis) | `cache.r6g.large` | 2 | 13.07 GiB | 기본 대역폭 0.75 Gbps로 AOF + 폭주 동시 처리 안정 |
| MSK (Kafka) | `kafka.m5.large` × 3 | 2×3 | 8×3 GiB (EC2 기준) | 300 msg/s + Debezium CDC 처리. 3브로커로 replication factor 3 충족 |

---

## D. AWS 스펙별 성능 예측

> 로컬 Gatling 측정값을 기준으로 각 스펙 조합에서 예상되는 성능을 추정한다.
> 예측은 실제 측정이 아닌 추정이며, Phase 12 배포 후 동일 Gatling 시나리오로 검증한다.

### D-1. 로컬 vs AWS 환경 차이 분석

로컬 측정값이 AWS 대비 낮게 나오는 구조적 이유:

| 항목 | 로컬 (Windows PC + Docker) | AWS |
|---|---|---|
| 서비스 격리 | Spring Boot + MySQL + Redis + Kafka + Debezium 전부 한 머신에서 CPU/RAM 공유 | 컴포넌트별 전용 인스턴스 — 리소스 경쟁 없음 |
| CPU | Windows PC CPU를 모든 서비스가 나눠 씀 | 앱 서버 c6i.xlarge 4 vCPU 단독 사용 |
| 네트워크 | localhost (0ms) | VPC 내부 (~0.3~1ms) — 거의 무시 가능 |
| Redis | Docker 컨테이너, 다른 서비스와 CPU 경쟁 | cache.r6g.large 전용 인스턴스 |
| Kafka | Docker 컨테이너, CPU 경쟁 | kafka.m5.large 전용 브로커 |

### D-2. 로컬 병목 구간 분석 (실제 측정 기반)

**시나리오 1 — 수강신청 플로우 (로컬 P95 2845ms, 156 req/s)**

| 구간 | 예상 소요시간 | 병목 원인 |
|---|---|---|
| 로그인 BCrypt 해싱 | ~200~400ms | Docker에서 CPU 경쟁. 300명 동시 로그인 시 해싱 큐 적체 |
| Redis DECR/GET (동기 구간) | ~2~5ms | 로컬에서도 빠름. 병목 아님 |
| Kafka 발행 (acks=all) | ~50~100ms | 브로커 응답 대기. 로컬 Docker에서 추가 지연 |
| 스레드 풀 큐잉 대기 | ~2000~2300ms | 300명 동시 → Spring 기본 스레드 200개 포화 → 대기 누적 |

> P95가 2845ms인 주원인은 스레드 풀 큐잉. 300명이 동시에 몰리면 상위 5% 요청은 스레드를 할당받을 때까지 수백~수천ms 대기한다. AWS 격리 환경에서는 BCrypt 처리 속도가 빨라져 스레드 반환이 빨라지고 큐잉 시간이 대폭 감소한다.

**시나리오 2 — 대기자 플로우 (로컬 P95 3503ms, 148 req/s)**

| 구간 | 예상 소요시간 | 병목 원인 |
|---|---|---|
| 로그인 BCrypt 해싱 | ~200~400ms | 시나리오 1과 동일 |
| 대기자 등록 Redis DECR | ~2~5ms | 병목 아님 |
| Kafka waitlist-promote-events 발행 | ~50~100ms | 단일 파티션 순서 보장 구간 |
| 스레드 풀 큐잉 대기 | ~2500~3000ms | 시나리오 1보다 높음 — 대기자 처리 플로우가 더 복잡 |

### D-3. AWS 스펙별 성능 예측

**시나리오 1 — 수강신청 플로우 (300명 동시)**

| 환경 | 앱 서버 | RDS | P95 예측 | 처리량 예측 | 에러율 | 근거 |
|---|---|---|---|---|---|---|
| 로컬 (실측) | Windows PC Docker | Docker MySQL | **2845ms** | **156 req/s** | 0% | Gatling 실측값 |
| AWS 최소 | c6i.large (2 vCPU) | db.t3.medium (4 GiB) | 1500~2000ms | 200~240 req/s | 0% 예상 | 격리 효과로 큐잉 감소. vCPU 2개 한계 + t3 버스트 제한 |
| AWS 권장 | c6i.xlarge (4 vCPU) | db.r5.large (16 GiB) | **700~1000ms** | **280~350 req/s** | 0% 예상 | 4 vCPU 전용으로 BCrypt 큐 해소. 서비스 격리로 큐잉 대기 대폭 감소 |
| AWS 확장 | c6i.2xlarge (8 vCPU) | db.r5.xlarge (32 GiB) | 500~700ms | 400~500 req/s | 0% 예상 | 수강신청 동기 구간이 Redis-only라 DB 스펙 영향 제한적. CPU 증가 효과가 주도 |

**시나리오 2 — 대기자 플로우 (300명 동시, 대기 슬롯 30개)**

| 환경 | 앱 서버 | RDS | P95 예측 | 처리량 예측 | 에러율 | 근거 |
|---|---|---|---|---|---|---|
| 로컬 (실측) | Windows PC Docker | Docker MySQL | **3503ms** | **148 req/s** | 0% | Gatling 실측값 (Kafka 단일 파티션 전환 후) |
| AWS 최소 | c6i.large (2 vCPU) | db.t3.medium (4 GiB) | 1800~2400ms | 180~220 req/s | 0% 예상 | 격리 효과. 단, vCPU 2개로 BCrypt + Consumer 경쟁 |
| AWS 권장 | c6i.xlarge (4 vCPU) | db.r5.large (16 GiB) | **800~1200ms** | **260~320 req/s** | 0% 예상 | BCrypt + Kafka Consumer 병렬 처리 여유. 단일 파티션 순서 보장 유지 |
| AWS 확장 | c6i.2xlarge (8 vCPU) | db.r5.xlarge (32 GiB) | 600~800ms | 360~440 req/s | 0% 예상 | Consumer 병렬 처리 증가. 대기자 플로우 특성상 시나리오 1 대비 개선 폭 낮음 |

### D-4. 예측 신뢰도 및 주의사항

- 예측값은 **범위(min~max)**로 표기 — 실제 수치는 네트워크 레이턴시, JVM GC, OS 스케줄링 등 변수에 따라 달라짐
- 수강신청 동기 구간은 Redis-only라 **DB 스펙 변경 영향이 제한적** — 앱 서버 CPU가 핵심 변수
- AWS 권장 스펙 기준 로컬 대비 **P95 약 65~75% 개선** 예상 (2845ms → 700~1000ms)
- Kafka `acks=all` 설정 유지로 브로커 응답 대기는 AWS에서도 동일하게 발생
- 실제 검증은 Phase 12 배포 후 동일 Gatling 시나리오(300명) 재실행으로 확인

---

## E. SLO (Service Level Objective)

> D섹션 예측 표(AWS 권장 스펙 기준)를 근거로 설정한 목표치다.
> Phase 12 배포 후 Gatling 재측정으로 달성 여부를 검증한다.

### E-1. SLO 정의

| 지표 (SLI) | 목표 (SLO) | 근거 |
|---|---|---|
| 수강신청 플로우 P95 응답시간 | **< 1000ms** | AWS 권장 스펙 예측 700~1000ms — 상한선 기준 |
| 대기자 플로우 P95 응답시간 | **< 1200ms** | AWS 권장 스펙 예측 800~1200ms — 상한선 기준 |
| 에러율 | **< 0.1%** | 로컬 0% 달성 중. AWS 격리 환경에서 동일 수준 유지 기대 |
| 동시 처리량 | **300명 동시 / 250 req/s 이상** | AWS 권장 스펙 예측 280~350 req/s — 하한선 기준 |
| 서비스 가용성 | **99.9% 이상** | 월 다운타임 43분 이하. EKS 자동 복구 + RDS Multi-AZ 기준 |

### E-2. SLO 달성 조건

SLO를 달성하려면 아래 인프라 조건이 전제되어야 한다.

| 조건 | 내용 |
|---|---|
| 앱 서버 | c6i.xlarge 이상 (4 vCPU 전용) |
| RDS | db.r5.large 이상 (InnoDB 버퍼 풀 11 GiB) |
| ElastiCache | cache.r6g.large 이상 (기본 대역폭 0.75 Gbps) |
| MSK | kafka.m5.large × 3 브로커 이상 |
| 서비스 격리 | 각 컴포넌트 별도 인스턴스 (로컬처럼 한 머신 공유 금지) |

### E-3. SLO 미달 시 대응

| 상황 | 원인 추정 | 대응 |
|---|---|---|
| P95 > 1000ms (수강신청) | BCrypt 큐잉 지속 | c6i.xlarge → c6i.2xlarge scale-up |
| P95 > 1200ms (대기자) | Consumer 처리 지연 | enrollment-events 3파티션 Consumer 병렬 처리 확장 |
| 에러율 > 0.1% | Redis 연결 불안정 또는 Kafka Consumer lag 적체 | Grafana Consumer lag 모니터링 + ElastiCache 연결 점검 |
| 처리량 < 250 req/s | 스레드 풀 포화 | Spring `server.tomcat.threads.max` 튜닝 또는 scale-out |
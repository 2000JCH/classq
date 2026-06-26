# Phase 11 피드백 (2026-06-18)

---

## 원문

> aws 만드는것도 테라폼이라고 있어. 그걸로 이용해서 코딩작업을 해놔

---

## 원문

> 로그인 > 수강신청 + 수강 변경까지 한번에 부하할것
> 프로세스는 함께 이어지기 때문에 그걸 받쳐주는게 가장 중요함
> 
> 1501ms 스펙이 너무 약함
> 처리에 대한 스펙을 늘리던가 해야하는데 본스펙에 맞지 않는 부하로 보임
> 인프라 스펙도 같이 적어서 놓을것
> 
> ex. RDS t3.small > c4.xlarge 스펙 변동으로 어디까지 개선이 될지 예측 정도만, 인프라 생성은 하지말것
> aws 스펙 권고에 맞춰서 공부해놓는것도 방법.
> c 계열 T계열 r계열에 대한 부분을 분석해보면 좋음

---

## TODO 체크리스트

- [x] Gatling 시나리오 수정 — 로그인 → 수강신청 → 취소 → 재신청 하나의 체인으로 (EnrollmentFlowSimulation)
- [x] Gatling 시나리오 수정 — 로그인 → 대기자 등록 → 취소 → 재등록 하나의 체인으로 (WaitlistFlowSimulation)
- [x] **[코드 수정]** `EnrollmentService.enroll()` 동기 구간 DB 쿼리 제거 → Redis-only로 수정 (2026-06-19 완료)
  - `DataInitializer`: 과목 생성 시 `course:{id}:credits`, `course:{id}:schedules` 캐싱 / 학생 생성 시 `student:account:{accountId}` 캐싱
  - `AccountService.signup()`: 학생 가입 시 `student:account:{accountId}` 캐싱 (Gatling before() 커버)
  - `AccountService.login()`: 학생 로그인 시 `student:account:{accountId}` 캐싱 (보완)
  - `EnrollmentService`: `enroll()` DB 쿼리 4개 → 0개 / `cancel()`, `enrollFromWaitlist()` 도 Redis 전환
  - JwtUtil, JwtFilter는 변경 없음 — studentId를 JWT 클레임 대신 Redis 캐시로 처리
- [x] 노션 부하 테스트 페이지 — 측정 환경 스펙 기재 (2026-06-19 완료)
- [x] **[재측정 완료]** EnrollmentFlowSimulation 재실행 → 노션 Before/After 수치 업데이트 (2026-06-19 완료, P95 4636ms → 2845ms, 처리량 109 → 156 req/s)
- [x] **[완료]** WaitlistFlowSimulation 실행 → 노션 시나리오 2 수치 기록 (2026-06-20 완료)
  - Before (DB 직접 조회): P95 3266ms, 처리량 142 req/s, HikariCP 대기 189, 500에러 25건
  - WaitlistService Redis 전환: register() DB 쿼리 6개 → 1~2개, Redisson 락 제거, rank Redis INCR
  - After (Redis): P95 4060ms, 처리량 124 req/s, HikariCP 대기 80 (-58%) — cancel() 데드락이 병목으로 P95 개선 없음
- [x] **[완료]** decrementRanksAfter 데드락 해결 — Redis Sorted Set(B안) 도입 (2026-06-21)
  - `waitlist:zset:course:{id}` Sorted Set으로 순번 관리 전환, bulk UPDATE 제거로 데드락 구조 근본 제거
  - 에러율 2.71% → 0%, HikariCP 커넥션 대기 ~0 달성
  - 코드래빗 리뷰 반영: stale ZSET head 루프 수정(CourseService, EnrollmentCancelConsumer, WaitlistService), 승격 시 lock TTL 재설정
- [x] **[완료]** 노션 부하 테스트 페이지 — After 2 (Sorted Set) 결과 업데이트 (2026-06-21)
- [ ] Gatling 시나리오 추가 — 알림 수락 플로우 (NotificationAcceptSimulation) → 보류
- [x] 노션 부하 테스트 페이지 — AWS 인스턴스 스펙별 성능 예측 표 추가 (2026-06-23 완료, 별도 'AWS 인프라 설계' 노션 페이지로 작성)
- [x] 노션 부하 테스트 페이지 — t/c/r 계열 특성 + ClassQ 권장 스펙 정리 (2026-06-23 완료, `.claude/docs/aws-spec.md` A~C섹션)
- [x] 목표 SLO 정의 후 문서 반영 (2026-06-23 완료, `.claude/docs/aws-spec.md` E섹션 — 수강신청 P95 < 1000ms, 에러율 < 0.1%, 처리량 > 250 req/s)
- [x] Terraform으로 AWS 인프라 코드 작성 (2026-06-23 완료, `terraform/` 모듈 분리 구조 — VPC, EKS, RDS, ElastiCache, MSK, ECR)

---

## 피드백 해석 메모

### "Terraform으로 코딩작업을 해놔"

AWS 인프라를 콘솔에서 클릭으로 만들지 말고, Terraform 코드로 정의해두라는 뜻.

- Terraform: Infrastructure as Code 도구. `.tf` 파일에 EC2, RDS, Redis, Kafka 등 인프라를 코드로 선언하고 `terraform apply` 한 번으로 환경을 생성/삭제한다.
- 왜 쓰나: 동일한 환경을 여러 번 재현 가능, 코드 리뷰 가능, 실수로 설정 누락하는 일 없음
- Phase 12 배포 전까지 `terraform/` 디렉토리에 EC2(앱 서버), RDS(MySQL), ElastiCache(Redis), MSK(Kafka), VPC, Security Group 등 리소스를 `.tf` 파일로 작성해두면 됨
- 실제 `terraform apply`는 배포 시점에 함 — 지금은 코드만 작성

---

### "처리에 대한 스펙을 늘려라 / 본 스펙에 맞지 않는 부하"

- "처리에 대한 스펙" = 서버 사양(인스턴스 성능)을 말함
- "본 스펙에 맞지 않는 부하" = 지금 로컬(맥북 + Docker)이라는 약한 환경에 300명 부하를 줬으니 당연히 느릴 수밖에 없음. 그 수치는 실제 서버 성능을 반영하지 못함
- 즉 "테스트 환경이 너무 약한데 거기에 300명 때렸으니 1501ms 나온 건 당연하고, 그게 의미있는 수치가 아니다"는 뜻

해결 방향 두 가지:
- 서버 스펙을 올리거나 (더 좋은 인스턴스)
- 현재 환경 스펙에 맞는 적절한 부하 수치를 쓰거나

---

### "RDS t3.small > c4.xlarge" — 지금 우리가 c4.xlarge 쓰고 있다는 건가?

아님. 예시로 든 것. "t3.small 수준(지금 로컬)에서 c4.xlarge(더 좋은 서버)로 바꾸면 얼마나 빨라질지 예측만 해보라"는 뜻. 실제로 c4.xlarge를 쓰고 있거나 쓰기로 했다는 게 아님.

---

### "인프라 생성은 하지말것" — AWS에 올리지 말라는 거?

맞음. AWS 콘솔에서 실제 인스턴스 만들지 말라는 뜻. 문서에 예측만 적어두면 되고, 실제 생성은 Phase 12 배포 때 함.

---

### "AWS 스펙 권고에 맞춰서 공부해놓는것도 방법" — 모델 하나 골라서 공부하라는 건가?

아님. AWS가 각 용도에 맞게 어떤 인스턴스를 권장하는지 개념을 공부해두라는 뜻.

- 웹 서버 → c 계열 권장
- DB → r 계열 권장
- 개발/저부하 → t 계열

이걸 이해하면 배포할 때 "우리 프로젝트엔 이 스펙이 맞다"고 근거 있게 선택할 수 있음.

---

### c/t/r 계열 — Grafana에서 보나 Gatling에서 보나?

Grafana도 Gatling도 아님. AWS 서버 이름 앞에 붙는 계열 이름.

```
t3.small   → t 계열
c5.xlarge  → c 계열
r5.large   → r 계열
```

| 계열 | 특징 | 약점 |
|---|---|---|
| t | 싸고 평소엔 절전 모드, 잠깐 빠름 (크레딧 방식) | 지속 부하 오면 성능 반토막 |
| c | CPU가 항상 일정하게 강함 | 메모리는 상대적으로 적음 |
| r | RAM이 엄청 많음 | CPU는 보통 수준 |

**ClassQ에 대입하면:**

| 컴포넌트 | 권장 계열 | 이유 |
|---|---|---|
| 앱 서버 | c 계열 | 로그인 BCrypt, Kafka 처리 — CPU 집약적. t 쓰면 폭주 때 크레딧 고갈 후 성능 급락 |
| RDS | r 계열 | InnoDB 버퍼 풀이 클수록 디스크 I/O 줄어서 쿼리 빠름 |
| ElastiCache Redis | r 계열 | Redis 자체가 전부 메모리에 올라가는 구조 — RAM이 클수록 안정 |
| Kafka | c 계열 | 메시지 직렬화/역직렬화 + 네트워크 처리 — CPU 위주 |

분석 결과는 AWS 문서 읽고 노션 부하 테스트 페이지에 정리하면 됨. Grafana/Gatling 리포트랑 관계없음.

---

## 알림 수락 시나리오 설계 (NotificationAcceptSimulation)

### 흐름

```
그룹 A (30명, 수강신청 완료 상태)
  → 로그인 → 수강신청 취소
  → Cancel Consumer: 대기 rank 1에게 NOTIFIED 설정

그룹 B (30명, 대기 중 상태) — 그룹 A 시작 3초 후 출발
  → 로그인 → GET /waitlists/me 폴링 (1초 간격, 최대 15회)
  → status = NOTIFIED 감지 → POST /waitlists/{id}/accept
```

### Gatling 구조

```java
setUp(
    그룹A_취소.injectOpen(atOnceUsers(30)),
    그룹B_수락.injectOpen(nothingFor(Duration.ofSeconds(3)), atOnceUsers(30))
).protocols(httpProtocol);
```

### before() — JDBC로 DB 직접 세팅

HTTP로 수강신청하면 Kafka 비동기라 before() 완료 보장 불가 → JDBC로 직접 INSERT

- 계정 1~30: enrollment 테이블 COMPLETED 행 INSERT (그룹 A용)
- 계정 31~60: waitlist 테이블 WAITING 행 INSERT, rank 1~30 (그룹 B용)
- Redis: enrollment:course:3 = 0 (정원 소진)

### 검증 포인트

| 항목 | 기대 동작 |
|---|---|
| 30명 동시 취소 | Cancel Consumer: 첫 번째만 rank 1 NOTIFIED, 나머지는 락으로 skip |
| lock:course:{id} | 중복 NOTIFIED 발생 안 함 |
| 대기자 수락 | DECR enrollment + Kafka + DEL lock 정상 처리 |
| 폴링 응답시간 | GET /waitlists/me 반복 호출 부하 측정 |

### 주의

30명이 동시에 취소해도 Cancel Consumer 락 구조 때문에 rank 1 한 명만 NOTIFIED됨.
rank 2~30은 rank 1이 수락/거절/만료될 때까지 WAITING 유지 → 이건 정상 동작이므로 KO 처리하면 안 됨.
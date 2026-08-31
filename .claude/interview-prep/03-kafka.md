# Kafka

## 1. 기본 개념

**구성 요소**: 프로듀서, 브로커, 컨슈머
- **프로듀서**: 메시지를 보내는 주체. **토픽은 코드에서 직접 지정**하고, **키(key)를 이용해 그 토픽 안 어떤 파티션으로 보낼지** 결정한다. (키가 없으면 라운드로빈으로 아무 파티션에나 분산)
- **브로커**: 메시지가 실제로 저장되는 서버. 브로커 하나가 특정 토픽 하나를 통째로 담는 게 아니라, **여러 토픽의 파티션들을 나눠서 저장**한다.
- **컨슈머**: 브로커에 저장된 메시지를 읽어가는 주체.

**토픽 / 파티션 관계**
- 토픽은 파티션들을 묶는 논리적인 이름표. 실제 데이터가 저장되는 물리적 단위는 **파티션**이다.
- 토픽 하나는 1개 이상의 파티션을 가져야 한다. 파티션이 여러 개면 처리량(병렬 처리)을 늘릴 수 있다.
- **순서 보장은 파티션 단위**로만 이루어진다. 같은 파티션 안에서는 들어온 순서대로 처리되지만, 파티션이 여러 개면 파티션 간 순서는 보장되지 않는다. (같은 key는 항상 같은 파티션으로 가므로, "같은 key끼리의 순서"는 보장됨)

**컨슈머 그룹**
- 같은 목적으로 하나(혹은 여러) 토픽을 나눠 처리하는 컨슈머들의 집합. **같은 그룹 안 컨슈머들은 보통 같은 토픽을 "같이" 구독**해서 파티션을 나눠 갖는다 (이게 그룹의 존재 이유).
- 단, **같은 그룹 안에서 같은 파티션을 2개 이상의 컨슈머가 동시에 읽을 수는 없다** — 파티션은 그룹 내에서 정확히 1개의 컨슈머에게만 배정된다.
- **토픽 구독은 코드에 고정**되어 있지만(`@KafkaListener(topics=...)`), **어떤 파티션을 담당할지는 런타임에 그룹 코디네이터가 동적으로 배정**한다 (리밸런스). 개발자가 "컨슈머1=파티션0"이라고 직접 못박는 게 아니다.

**오프셋**
- 파티션 안에서 각 메시지의 순차적인 위치 번호. 컨슈머가 어디까지 읽었는지를 나타내며, `(그룹ID, 토픽, 파티션)` 단위로 Kafka 내부에 커밋되어 저장된다 (컨슈머 개체의 메모리가 아님).
- 컨슈머가 죽으면 리밸런스가 일어나 남은 컨슈머 중 하나가 그 파티션을 이어받고, **마지막 커밋 오프셋부터** 이어서 읽는다 (처음부터 다시 X).
- 복구된 컨슈머가 다시 합류하면 또 리밸런스가 일어나며, 원래 담당하던 파티션을 그대로 돌려받는다는 보장은 없다.

**복제(Replication)**
- 복제는 **파티션 단위**로 이루어진다. 파티션마다 리더 1개 + 팔로워 여러 개로 복제되며, 평소엔 리더만 읽고 쓰며 팔로워는 리더를 따라 갱신한다.
- 리더가 죽으면 정상적으로 복제를 따라온 팔로워(ISR) 중에서 새 리더가 선출된다.

---

## 2. 우리 프로젝트 적용

**프로듀서**

| 발행 주체 | 토픽 | 키 |
|---|---|---|
| `EnrollmentService` | `enrollment-events`, `enrollment-cancel-events` | `studentId` |
| `WaitlistService` | `waitlist-promote-events` | `courseId` |
| Debezium (앱 코드 아님, MySQL binlog 자동 감지) | `course-events` | — |
| 에러 핸들러(자동 발행) | `enrollment-dead-letter` | — |

설정(`application.yml`): `acks: all` (모든 복제본 확인 후 성공 응답 → 메시지 유실 방지)

**브로커**
- 로컬: 1대, KRaft 모드(Zookeeper 없음), `REPLICATION_FACTOR: 1`
- 목표(AWS): MSK `kafka.m5.large × 3` — Terraform만 작성, 실제 연결은 Phase 12 미완료 항목

**토픽 / 파티션**

| 토픽 | 파티션 | 이유 |
|---|---|---|
| `enrollment-events` | 3 | 수강신청 폭주 시 컨슈머 병렬 확장 → 처리량 확보 |
| `enrollment-cancel-events` | 1 | 저빈도 |
| `course-events` | 1 | 저빈도 (교수 액션 시만) |
| `waitlist-promote-events` | 1 | 대기 순번 순서 보장 필수 |
| `enrollment-dead-letter` | 1 | 실패 메시지 격리용 |

**컨슈머**
- 컨슈머 그룹: `enrollment-processor` 1개, 총 컨슈머 6개
  - `enrollment-events` 담당 3개 (`concurrency=3`, 파티션 수와 1:1)
  - `enrollment-cancel-events` / `course-events` / `waitlist-promote-events` 담당 각 1개
- `enable-auto-commit: false` — 리스너 메서드 처리가 정상 끝난 후에만 오프셋 커밋 (at-least-once 보장)
- `auto-offset-reset: earliest` — 커밋된 오프셋이 없으면 처음부터 읽음

**장애 대응**
- 재시도 3회(`FixedBackOff(0L, 3L)`) 후 실패하면 `enrollment-dead-letter`로 격리
- 멱등성 처리: `EnrollmentConsumer`/`EnrollmentCancelConsumer`가 재전달로 같은 메시지를 두 번 받아도 이미 COMPLETED/CANCELLED면 skip (Phase 9 coderabbit 리뷰 보완)
- `afterCommit`으로 DB 커밋 성공 후에만 Redis 갱신 (트랜잭션 롤백 시 Redis-DB 불일치 방지)
- 컨슈머 장애 시 리밸런스 자체는 Kafka/Spring Kafka 프레임워크가 자동 처리, 별도 코드 없음

**멱등성 체크 상세**

"같은 메시지를 두 번 받는다"는 게 무슨 상황인지:

1. 학생이 수강신청 버튼을 한 번만 누름
2. 프로듀서가 `enrollment-events`에 메시지를 한 개만 보냄
3. 컨슈머가 그 메시지를 받아서 DB INSERT까지 정상적으로 끝냄
4. 근데 그 직후, 오프셋을 커밋하기 직전에 컨슈머가 죽거나(서버 재시작, 리밸런스 등) 타이밍이 꼬임
5. Kafka 입장에서는 "이 메시지 처리 끝났다"는 기록(오프셋 커밋)이 없으니까, 그 파티션을 이어받은 컨슈머(또는 재시작된 컨슈머)한테 똑같은 메시지를 처음부터 다시 전달함
6. 이 재전달된 메시지가 `consume()`에 다시 들어왔을 때, 멱등성 체크 없이 그대로 INSERT를 또 하면 → 이미 있는 `(studentId, courseId)` 조합이라 unique constraint 위반이 나거나, 체크가 허술하면 학점이 중복으로 쌓이는 문제가 생김

→ 즉 학생이 버튼을 두 번 눌러서가 아니라, **Kafka의 at-least-once 전달 보장 때문에 시스템 내부에서 같은 메시지가 재전달**되는 상황.

확인 위치는 **DB(RDS), Redis 아님** — `EnrollmentConsumer.java`에서 기존 행 상태(`CANCELLED`/`COMPLETED`/없음)를 직접 조회해서 갈라 처리함.

**afterCommit으로 Redis-DB 불일치 방지 — `CourseService.createCourse()` 예시**

핵심 전제: **"메서드가 예외 없이 끝났다" ≠ "커밋이 완료됐다"**. 실제 flush/commit은 메서드 몸통이 다 끝난 다음, 스프링 트랜잭션 프록시가 메서드 바깥에서 별도로 처리하는 단계라 그 사이에 (드물게) 커밋이 실패할 수 있다.

```java
courseRepository.save(course);                    // 121줄 — Course 저장
for (스케줄) {
    courseScheduleRepository.save(schedule);       // 131줄 — CourseSchedule 저장(반복)
}
long savedCourseId = course.getId();
registerSynchronization(afterCommit -> {           // 136줄
    redis.set("enrollment:course:" + savedCourseId, capacity);   // 139줄
    redis.set("waitlist:course:" + savedCourseId, waitlistLimit); // 140줄
});
```

`courseRepository.save()`, `courseScheduleRepository.save()`는 JPA라서 호출한다고 바로 MySQL에 INSERT문이 나가는 게 아니라, 실제 INSERT는 **트랜잭션이 커밋되는 순간(flush)**에 몰아서 나간다. 즉 121~131줄까지 자바 코드가 예외 없이 멀쩡히 지나갔어도, 진짜 DB 저장은 아직 확정 안 된 상태다.

- **afterCommit 없이 121번 줄 밑에 Redis 코드를 바로 뒀다면**: 자바 코드가 순서대로 실행되며 Redis에 `enrollment:course:{id} = 정원 30`이 바로 반영됨. 근데 그 직후 실제 DB 커밋이 실패하면 → DB엔 그 강의(Course row)가 없는데 Redis엔 "정원 30명짜리 강의"가 살아있는 상태가 됨. 이 상태에서 학생이 그 강의를 수강신청하면 Redis 체크는 통과하고 Kafka까지 발행되는데, 정작 비동기 컨슈머가 `courseRepository.getReferenceById(courseId)`로 그 강의를 찾으려 하면 존재하지 않는 강의라 처리가 깨짐
- **afterCommit을 쓰면**: 등록만 해두고, 진짜 커밋이 성공했다는 걸 스프링이 확인해준 다음에야 139~140줄이 실행됨. "DB엔 없는데 Redis엔 있는 유령 강의"가 생길 가능성 자체가 사라짐

→ 강의 등록에서 afterCommit을 쓴 이유는, 자바 코드가 예외 없이 끝나는 것과 실제 DB에 확정 저장되는 것 사이의 시간차 동안 Redis가 먼저 앞서나가지 않도록, Redis 반영을 진짜 커밋 확정 시점까지 묶어둔 것.

**afterCommit이 필요한 조건 (일반화)**

"Redis, Kafka가 없었다면 afterCommit이 필요 없다"보다 정확한 조건은 **"DB 트랜잭션에 안 묶이는 외부 시스템이 하나도 없었다면"**이다. 순수하게 DB만 쓰는 메서드라면 afterCommit이 필요 없다 — 메서드 안에서 일어나는 모든 변경이 같은 트랜잭션 안의 DB 작업이라, 뭔가 실패하면 전부 다 같이 롤백되므로 되돌릴 수 없는 부수 효과 자체가 없기 때문이다.

Redis·Kafka는 이 프로젝트에서 "트랜잭션 관리 대상이 아닌 외부 시스템"의 구체적인 사례일 뿐이고, 같은 문제는 이메일 발송·외부 API 호출·SSE 알림·파일 저장 등 DB 트랜잭션과 함께 묶이는 어떤 비-DB 부수 효과에도 동일하게 적용된다.

**Consumer별 afterCommit 사용 목적 — 3가지 유형**

같은 코드 패턴(`registerSynchronization().afterCommit()`)인데 컨슈머마다 막으려는 문제가 다르다.

| 컨슈머 | afterCommit 안에서 하는 일 | 막으려는 문제 |
|---|---|---|
| `EnrollmentConsumer` | `schedule:student`, `credits:student` 갱신 | **롤백 방지형** — DB insert가 롤백되면 Redis 학생 캐시에 실제로 없는 수강신청 내역이 반영된 채 남는 것 |
| `EnrollmentCancelConsumer` / `WaitlistPromoteConsumer` | `lock:course` 설정 + SSE 발송 | **경쟁상태 방지형** — 에러 유무와 무관하게, 커밋 전에 SSE가 먼저 도착하면 클라이언트가 DB에 아직 안 보이는 데이터를 조회하게 됨 (정상 흐름에서도 항상 존재하는 타이밍 문제) |
| `CourseEventConsumer` | 학생별 Redis 캐시 정리 + 강의 단위 키 삭제 + SSE 발송 | **위 두 이유가 동시에 적용** + 다수 학생 대상이라는 점이 다름 |

**CourseEventConsumer 폐강 처리 흐름**

```
폐강
→ binlog 변경 감지
→ Debezium 발행
→ course-events 토픽에 메시지 저장
→ course-events를 구독 중이던 컨슈머가 메시지를 읽음
→ DB 업데이트   (이 단계 안에서 알림 큐에도 미리 쌓아둠 — 수강생 먼저, 대기자 나중)
→ DB 커밋 완료
→ afterCommit 실행
→ Redis 값 업데이트 + 큐에 쌓아둔 걸 순서대로 알림 발송
```

- Debezium은 `course` 테이블만 감시한다 (학생 액션은 `enrollment` 테이블만 건드리고 `course` 테이블은 안 건드려서 여기 안 걸림). 감지한 변경을 `course-events`에 발행하는 것까지만 하고, 폐강인지 정원 변경인지 판단하는 로직은 전혀 없음 — 그건 컨슈머 몫.
- 큐는 2개, 독립적: `sseQueue`(알림용, 수강생+대기자 둘 다 순서대로 들어감), `enrolledStudentIds`(Redis 정리용, 수강생만). 하나가 다른 하나를 포함하는 구조 아님.
- 큐에 "쌓는 것"과 "발송하는 것"은 시점이 다르다 — 쌓는 건 DB 업데이트 단계에서 커밋 전에 이미 끝나고(자바 메모리 연산이라 커밋 여부와 무관), 실제 발송은 afterCommit에서 커밋 확인 후에 이뤄짐.

**현재 한계**
- 로컬은 브로커 1대라 복제·브로커 장애 대응이 사실상 검증 안 됨. AWS MSK(3대) 연결 후에야 실전 검증 가능 — Phase 12 진행 시 확인 필요

---

**참고 문서**: `.claude/docs/architecture.md` 181~195줄 (토픽/파티션 표, 파티션 수 설계 이유). 오프셋/리밸런스/컨슈머 그룹 내부 동작은 프로젝트 문서에 없고 Kafka 자체의 범용 동작 원리.

# ClassQ Redis Key 설계

## 설정

Redis는 AOF(Append Only File) 방식으로 설정한다. 수강신청 잔여 자리, 학생 시간표, 학점 등 휘발되면 안 되는 데이터를 Redis에서 관리하기 때문이다. AOF는 모든 쓰기 명령을 디스크에 순차 기록하여 Redis 서버가 재시작되더라도 전체 데이터를 복원할 수 있도록 보장한다. `appendonly yes` 설정을 반드시 적용한다.

---

## Key 목록

### 1. 수강 잔여 자리

```
key:   enrollment:course:{courseId}
value: 잔여 자리 수 (Integer)
TTL:   없음
초기화: 강의 등록 시 capacity 값으로 SET
예시:  enrollment:course:101 = 3
```

TTL을 두지 않는 이유는 강의가 폐강되거나 관리자가 삭제하기 전까지 항상 유효한 값이어야 하기 때문이다. TTL로 만료되면 수강신청 시 자리가 있음에도 마감으로 응답하는 오류가 발생할 수 있다.

**사용 시점**
- 수강신청 시 DECR → 음수면 INCR 롤백 후 마감 응답
- 수강 취소 시 INCR
- 강의 정원 변경 시 Consumer가 RDS COUNT 쿼리로 재계산 후 SET
- key 없을 때 RDS에서 자동 복구 (capacity - COMPLETED enrollment 수)

---

### 2. 대기 잔여 자리

```
key:   waitlist:course:{courseId}
value: 잔여 대기 자리 수 (Integer)
TTL:   없음
초기화: 강의 등록 시 waitlist_limit 값으로 SET
예시:  waitlist:course:101 = 2
```

TTL을 두지 않는 이유는 enrollment:course:{id}와 동일하다. 대기 자리는 강의가 살아있는 동안 항상 유효해야 한다.

**사용 시점**
- 대기 등록 시 DECR → 음수면 INCR 롤백 후 대기 불가 응답
- 대기자 취소/만료/완료 시 INCR
- 강의 정원 변경 시 재계산 후 SET

---

### 3. 수강신청 잠금

```
key:   lock:course:{courseId}
value: "LOCKED" (String)
TTL:   없음 (Scheduler가 직접 관리)
예시:  lock:course:101 = "LOCKED"
```

TTL을 두지 않는 이유는 대기자 처리 완료 시점이 일정하지 않기 때문이다. TTL로 잠금이 자동 해제되면 대기자 처리 도중 다른 학생이 자리를 가져가는 경쟁 상태가 발생한다. Scheduler가 대기자 처리 완료를 확인한 후 직접 DEL로 해제한다.

**사용 시점**
- 수강 취소 발생 + 대기자 있을 때 SET
- 잠금 중 수강신청 시도 → "현재 대기자 처리 중입니다" 응답
- 잠금 해제 조건
  - 대기자 수락 완료
  - 모든 대기자 만료 후 다음 순번 없음
  - 강의 폐강

---

### 4. 학생 시간표 캐시

```
key:   schedule:student:{studentId}
value: JSON 배열
TTL:   없음
예시:  schedule:student:1 = [
         {"day":"MON","start":"09:00","end":"11:00"},
         {"day":"TUE","start":"13:00","end":"15:00"}
       ]
```

TTL을 두지 않는 이유는 수강신청 동기 구간에서 RDS 조회를 완전히 제거하기 위해서다. TTL로 캐시가 만료되면 수강신청 폭주 구간에 RDS 조회가 다시 발생한다. 시간표 변경은 수강신청/취소 시 즉시 갱신하므로 TTL 없이도 데이터 정합성이 유지된다.

**사용 시점**
- 수강신청 시 시간표 중복 체크 (RDS 조회 없이 Redis에서 비교)
- 캐시 없을 때 RDS에서 조회 후 저장 (credits와 한 번에 조회)
- 수강신청 완료 후 Consumer가 해당 과목 시간 추가
- 수강 취소 시 즉시 해당 과목 시간 삭제

**중복 체크 기준**
- 일부라도 겹치면 거절
- 딱 끝나는 시간은 겹침 아님 (09:00~11:00 / 11:00~13:00 → 통과)

---

### 5. 학생 총 학점 캐시

```
key:   credits:student:{studentId}
value: 현재 총 학점 (Integer)
TTL:   없음
예시:  credits:student:1 = 15
```

TTL을 두지 않는 이유는 schedule:student:{id}와 동일하다. 학점 체크는 수강신청 동기 구간에서 수행되므로 캐시가 항상 살아있어야 RDS 조회를 제거할 수 있다.

**사용 시점**
- 수강신청 시 학점 초과 체크 (현재 학점 + 신청 과목 학점 > 19이면 거절)
- 캐시 없을 때 schedule:student:{id}와 한 번에 RDS에서 조회 후 저장
- 수강신청 완료 후 Consumer가 INCRBY {credits}
- 수강 취소 시 즉시 DECRBY {credits}

---

### 6. Refresh Token

```
key:   refresh:token:{accountId}
value: refresh token 값 (String)
TTL:   7일 (604800초)
예시:  refresh:token:1 = "eyJhbGci..."
```

Refresh Token은 7일 TTL을 적용한다. 보안상 일정 기간이 지나면 자동 만료되어야 하며, 로그아웃 시 DEL로 즉시 무효화한다. 동일 계정 재로그인 시 덮어씌워 1계정 1토큰을 유지한다.

**사용 시점**
- 로그인 성공 시 SET (TTL 7일)
- access token 재발급 시 GET으로 유효성 검증
- 로그아웃 시 DEL로 즉시 만료
- 동일 계정 재로그인 시 덮어씌움 (1계정 1토큰)

---

## 전체 Key 요약

| Key | 초기화 시점 | 갱신 시점 | TTL |
|---|---|---|---|
| `enrollment:course:{id}` | 강의 등록 | 수강신청/취소/정원변경 | 없음 |
| `waitlist:course:{id}` | 강의 등록 | 대기 등록/취소/만료 | 없음 |
| `lock:course:{id}` | 대기자 처리 시작 | Scheduler가 해제 | 없음 |
| `schedule:student:{id}` | 첫 수강신청 시 | 수강신청/취소 | 없음 |
| `credits:student:{id}` | 첫 수강신청 시 | 수강신청/취소 | 없음 |
| `refresh:token:{accountId}` | 로그인 시 | 재로그인 시 덮어씌움 | 7일 |

---

## 장애 대비

Redis 서버가 재시작되더라도 AOF로 전체 데이터를 복원한다. enrollment key가 없을 때는 RDS COUNT 쿼리로 자동 복구 후 SET한다. schedule/credits key가 없을 때는 RDS에서 한 번에 조회 후 SET한다. lock key가 없을 때는 잠금이 없는 것으로 간주하여 정상 신청을 허용한다. refresh token key가 없을 때는 재로그인을 요청한다.

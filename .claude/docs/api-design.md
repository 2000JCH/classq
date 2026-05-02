# ClassQ API 설계

## 공통 규칙

Base URL은 `/api/v1`로 통일한다. 모든 인증이 필요한 요청은 `Authorization: Bearer {token}` 헤더로 JWT를 전달한다. 페이지네이션은 `?page=0&size=20` offset 방식을 사용하며, 모든 응답은 JSON 형식으로 반환한다.

---

## 인증 (Auth)

인증 API는 JWT 발급과 갱신을 담당한다. 로그인 성공 시 access token(단기)과 refresh token(장기, 7일)을 함께 발급한다. access token 만료 시 `/auth/refresh`로 재발급한다. 로그아웃 시 Redis에서 refresh token을 즉시 DEL하여 재발급을 차단한다.

| 메서드 | 엔드포인트 | 설명 | 권한 |
|---|---|---|---|
| POST | /api/v1/auth/signup | 회원가입 | 없음 |
| POST | /api/v1/auth/login | 로그인 (JWT 발급) | 없음 |
| POST | /api/v1/auth/logout | 로그아웃 (Redis refresh token 삭제) | 인증 필요 |
| POST | /api/v1/auth/refresh | access token 재발급 | 없음 |

---

## 학생 (Student)

학생 본인의 정보를 조회하고 수정한다. 타 학생 정보 조회는 제공하지 않는다.

| 메서드 | 엔드포인트 | 설명 | 권한 |
|---|---|---|---|
| GET | /api/v1/students/me | 내 정보 조회 | STUDENT |
| PUT | /api/v1/students/me | 내 정보 수정 | STUDENT |
| DELETE | /api/v1/students/me | 회원 탈퇴 (soft delete) | STUDENT |

---

## 교수 (Professor)

교수 본인의 정보를 조회하고 수정한다.

| 메서드 | 엔드포인트 | 설명 | 권한 |
|---|---|---|---|
| GET | /api/v1/professors/me | 내 정보 조회 | PROFESSOR |
| PUT | /api/v1/professors/me | 내 정보 수정 | PROFESSOR |

---

## 학과 (Department)

학과 목록은 수강신청 필터링과 강의 등록 시 참조 데이터로 사용된다. 조회만 제공하며 학과 생성/수정/삭제는 관리자 기능으로 분리한다.

| 메서드 | 엔드포인트 | 설명 | 권한 |
|---|---|---|---|
| GET | /api/v1/departments | 전체 학과 목록 조회 | 인증 필요 |

---

## 강의 (Course)

강의 목록은 다양한 조건으로 필터링할 수 있다. 강의 등록/수정/폐강은 교수만 가능하며, 폐강 시 Debezium이 course 테이블 변경을 감지하여 Kafka로 이벤트를 발행하고 Consumer가 수강/대기 학생에게 알림을 발송한다.

| 메서드 | 엔드포인트 | 설명 | 권한 |
|---|---|---|---|
| GET | /api/v1/courses | 강의 목록 조회 (필터링 가능) | 인증 필요 |
| GET | /api/v1/courses/{courseId} | 강의 상세 조회 | 인증 필요 |
| POST | /api/v1/courses | 강의 등록 | PROFESSOR |
| PUT | /api/v1/courses/{courseId} | 강의 수정 | PROFESSOR |
| DELETE | /api/v1/courses/{courseId} | 강의 폐강 | PROFESSOR |
| GET | /api/v1/courses/{courseId}/schedules | 강의 시간표 조회 | 인증 필요 |

**강의 목록 조회 필터 파라미터**
```
?courseType=MAJOR_REQUIRED
?classType=THEORY
?classMode=ONLINE
?departmentId=1
?page=0&size=20
```

---

## 수강신청 (Enrollment)

수강신청은 동기 구간과 비동기 구간으로 분리된다. 동기 구간에서는 Redis만 사용하여 잠금/시간표/학점/잔여자리를 체크하고 Kafka에 이벤트를 발행한 후 즉시 응답한다. 비동기 구간에서 Consumer가 RDS에 enrollment를 INSERT하고 Redis 캐시를 갱신한다. 이 구조로 폭주 구간에서 RDS 부하를 완전히 제거한다.

| 메서드 | 엔드포인트 | 설명 | 권한 |
|---|---|---|---|
| POST | /api/v1/enrollments | 수강신청 | STUDENT |
| DELETE | /api/v1/enrollments/{enrollmentId} | 수강신청 취소 | STUDENT |
| GET | /api/v1/enrollments/me | 내 수강신청 목록 조회 | STUDENT |

**수강신청 요청 body**
```json
{
  "courseId": 101
}
```

**수강신청 성공 응답**
```json
{
  "status": "SUCCESS",
  "message": "신청 완료",
  "courseId": 101,
  "courseName": "자바프로그래밍"
}
```

**수강신청 실패 응답**
```json
{
  "status": "FAIL",
  "message": "마감됨 | 학점 초과 | 시간 중복 | 대기자 처리 중"
}
```

---

## 대기자 (Waitlist)

대기자 등록은 수강 자리가 없을 때만 가능하다. 자리가 나면 순번대로 알림을 발송하며, 대기자는 10분 내에 수락해야 한다. 수락 시 학점 초과나 시간 중복이면 자동으로 다음 순번으로 넘어간다. 내 대기 목록 조회 응답에 currentCredits와 maxCredits를 포함하여 프론트엔드가 학점 초과 경고를 표시할 수 있도록 한다.

| 메서드 | 엔드포인트 | 설명 | 권한 |
|---|---|---|---|
| POST | /api/v1/waitlists | 대기자 등록 | STUDENT |
| DELETE | /api/v1/waitlists/{waitlistId} | 대기자 취소 | STUDENT |
| GET | /api/v1/waitlists/me | 내 대기 목록 조회 | STUDENT |
| POST | /api/v1/waitlists/{waitlistId}/accept | 대기 수락 | STUDENT |
| POST | /api/v1/waitlists/{waitlistId}/reject | 대기 거절 | STUDENT |

**내 대기 목록 조회 응답**
```json
{
  "waitlists": [
    {
      "waitlistId": 1,
      "courseId": 101,
      "courseName": "자바프로그래밍",
      "rank": 2,
      "status": "WAITING"
    }
  ],
  "currentCredits": 15,
  "maxCredits": 19
}
```

---

## 알림 (Notification)

알림은 수강신청 관련 이벤트(대기자 알림, 강의 폐강 등)가 발생할 때 Consumer가 notification 테이블에 INSERT한다. 읽음 처리 시 read_at을 현재 시각으로 UPDATE한다.

| 메서드 | 엔드포인트 | 설명 | 권한 |
|---|---|---|---|
| GET | /api/v1/notifications | 내 알림 목록 조회 | STUDENT |
| PATCH | /api/v1/notifications/{notificationId}/read | 알림 읽음 처리 (read_at 갱신) | STUDENT |

---

## 관리자 (Admin)

관리자는 전체 학생, 강의, 수강신청 현황을 조회하고 관리할 수 있다. 강제 탈퇴와 강제 폐강 기능을 제공한다.

| 메서드 | 엔드포인트 | 설명 | 권한 |
|---|---|---|---|
| GET | /api/v1/admin/students | 전체 학생 목록 조회 | ADMIN |
| DELETE | /api/v1/admin/students/{studentId} | 특정 학생 강제 탈퇴 | ADMIN |
| GET | /api/v1/admin/courses | 전체 강의 목록 조회 | ADMIN |
| DELETE | /api/v1/admin/courses/{courseId} | 특정 강의 강제 폐강 | ADMIN |
| GET | /api/v1/admin/courses/{courseId}/enrollments | 특정 강의 수강신청 현황 조회 | ADMIN |
| GET | /api/v1/admin/courses/{courseId}/waitlists | 특정 강의 대기자 명단 조회 | ADMIN |
| GET | /api/v1/admin/stats/enrollments | 전체 수강신청 현황 통계 | ADMIN |

**수강신청 현황 통계 응답**
```json
{
  "todayCount": 1250,
  "totalCount": 45000,
  "cancelledCount": 320
}
```

---

## 에러 응답 형식

모든 에러는 code와 message를 포함한 JSON으로 응답한다.

```json
{
  "code": "ENROLLMENT_CLOSED",
  "message": "수강 신청이 마감되었습니다."
}
```

| 코드 | 설명 |
|---|---|
| ENROLLMENT_CLOSED | 수강 자리 마감 |
| WAITLIST_CLOSED | 대기 자리 마감 |
| ENROLLMENT_LOCKED | 대기자 처리 중 |
| CREDIT_EXCEEDED | 학점 초과 |
| TIME_CONFLICT | 시간표 중복 |
| DUPLICATE_ENROLLMENT | 이미 신청한 강의 |
| UNAUTHORIZED | 인증 필요 |
| FORBIDDEN | 권한 없음 |

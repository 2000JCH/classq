# ClassQ RDS 테이블 설계

## 설계 원칙

모든 테이블은 soft delete를 기본 원칙으로 적용한다. 단, 삭제가 사실상 발생하지 않는 테이블(department, course_schedule)과 이력 추적이 불필요한 테이블(notification)은 예외로 한다. 인증은 JWT만 사용하며 소셜 로그인은 구현하지 않는다.

---

## 테이블 목록

1. account
2. student
3. professor
4. department
5. course
6. course_schedule
7. enrollment
8. waitlist
9. notification

---

## 1. account

인증 정보 테이블로, 학생/교수/관리자 모두 이 테이블을 통해 로그인한다. JWT 인증만 사용하므로 password를 필수값으로 관리한다. role 컬럼으로 JWT 권한을 구분(STUDENT / PROFESSOR / ADMIN)하며, soft delete를 적용하여 탈퇴 이력을 보존한다.

```sql
CREATE TABLE account (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  email      VARCHAR(100) NOT NULL UNIQUE,
  password   VARCHAR(255) NOT NULL,
  role       ENUM('STUDENT', 'PROFESSOR', 'ADMIN') NOT NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  PRIMARY KEY (id)
);
```

---

## 2. student

학생 정보 테이블이다. grade는 INT로 관리하며 상한을 두지 않는다. 유급이나 초과학기 학생이 존재할 수 있기 때문이다. account_id에 UNIQUE 제약을 걸어 1계정 1학생 관계를 보장하며, soft delete를 적용한다.

```sql
CREATE TABLE student (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  account_id    BIGINT      NOT NULL UNIQUE,
  department_id BIGINT      NOT NULL,
  name          VARCHAR(50) NOT NULL,
  grade         INT         NOT NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at    DATETIME,
  PRIMARY KEY (id),
  FOREIGN KEY (account_id)    REFERENCES account(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);
```

---

## 3. professor

교수 정보 테이블이다. account_id에 UNIQUE 제약을 걸어 1계정 1교수 관계를 보장하며, soft delete를 적용한다.

```sql
CREATE TABLE professor (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  account_id    BIGINT      NOT NULL UNIQUE,
  department_id BIGINT      NOT NULL,
  name          VARCHAR(50) NOT NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at    DATETIME,
  PRIMARY KEY (id),
  FOREIGN KEY (account_id)    REFERENCES account(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);
```

---

## 4. department

학과 테이블이다. 학부/학과 계층 구조 없이 학과만 단순 관리한다. 학과가 삭제되는 일이 사실상 없으므로 soft delete를 적용하지 않는다.

```sql
CREATE TABLE department (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  name       VARCHAR(100) NOT NULL UNIQUE,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
```

---

## 5. course

강의 정보 테이블이다. department_id가 NULL이면 교양 강의(학과 제한 없음)를 의미한다. min_grade / max_grade가 NULL이면 학년 제한이 없음을 의미한다. waitlist_limit은 교수가 강의 등록 시 직접 설정한다. status CLOSED는 폐강 상태를 나타내며, soft delete를 적용한다.

```sql
CREATE TABLE course (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  professor_id   BIGINT      NOT NULL,
  department_id  BIGINT,
  name           VARCHAR(100) NOT NULL,
  course_type    ENUM('MAJOR_REQUIRED', 'MAJOR_ELECTIVE', 'LIBERAL_ARTS') NOT NULL,
  class_type     ENUM('THEORY', 'PRACTICE') NOT NULL,
  class_mode     ENUM('ONLINE', 'OFFLINE') NOT NULL,
  credits        TINYINT     NOT NULL,
  capacity       INT         NOT NULL,
  waitlist_limit INT         NOT NULL DEFAULT 0,
  min_grade      INT,
  max_grade      INT,
  status         ENUM('ACTIVE', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at     DATETIME,
  PRIMARY KEY (id),
  FOREIGN KEY (professor_id)  REFERENCES professor(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);
```

---

## 6. course_schedule

강의 시간표 테이블이다. 하나의 강의가 여러 시간대를 가질 수 있어 course 테이블에서 분리했다(예: 자바프로그래밍 → 월 09:00~11:00, 수 09:00~11:00). 시간표 중복 체크는 이 테이블 기준으로 판단한다. course 삭제 시 함께 삭제되므로 soft delete를 적용하지 않는다.

```sql
CREATE TABLE course_schedule (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  course_id  BIGINT NOT NULL,
  day        ENUM('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN') NOT NULL,
  start_time TIME   NOT NULL,
  end_time   TIME   NOT NULL,
  PRIMARY KEY (id),
  FOREIGN KEY (course_id) REFERENCES course(id)
);
```

---

## 7. enrollment

수강신청 내역 테이블이다. PENDING 상태를 두지 않는다. Redis AOF와 Kafka acks=all 조합으로 유실을 방지하므로 중간 상태 관리가 불필요하기 때문이다. UNIQUE KEY로 동일 과목 중복 신청을 방지한다. soft delete를 적용하여 취소 이력을 추적하며, 잔여 자리 복구 시 COUNT 쿼리로 현재 신청 인원을 계산한다.

```sql
CREATE TABLE enrollment (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  student_id BIGINT   NOT NULL,
  course_id  BIGINT   NOT NULL,
  status     ENUM('COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'COMPLETED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  PRIMARY KEY (id),
  UNIQUE KEY uq_enrollment (student_id, course_id),
  FOREIGN KEY (student_id) REFERENCES student(id),
  FOREIGN KEY (course_id)  REFERENCES course(id)
);
```

---

## 8. waitlist

대기자 내역 테이블이다. rank 컬럼으로 대기 순번을 관리한다. Scheduler가 만료를 감지할 수 있도록 status와 expired_at을 함께 관리한다. expired_at은 알림 발송 시 현재시간 + 10분으로 세팅한다. UNIQUE KEY로 동일 과목 중복 대기를 방지하며, soft delete를 적용한다.

```sql
CREATE TABLE waitlist (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  student_id BIGINT   NOT NULL,
  course_id  BIGINT   NOT NULL,
  rank       INT      NOT NULL,
  status     ENUM('WAITING', 'NOTIFIED', 'EXPIRED', 'COMPLETED') NOT NULL DEFAULT 'WAITING',
  expired_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  PRIMARY KEY (id),
  UNIQUE KEY uq_waitlist (student_id, course_id),
  FOREIGN KEY (student_id) REFERENCES student(id),
  FOREIGN KEY (course_id)  REFERENCES course(id)
);
```

---

## 9. notification

알림 발송 내역 테이블이다. 모든 알림을 한 테이블에서 통합 관리하며, type 컬럼으로 알림 종류를 구분한다. read_at 컬럼으로 읽음 상태와 읽은 시각을 함께 관리한다. read_at이 NULL이면 미읽음, 값이 있으면 읽음 상태다. 알림은 읽고 나면 이력 추적이 불필요하므로 soft delete를 적용하지 않는다.

```sql
CREATE TABLE notification (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  student_id BIGINT       NOT NULL,
  course_id  BIGINT       NOT NULL,
  type       ENUM(
               'WAITLIST_AVAILABLE',
               'WAITLIST_EXPIRED',
               'WAITLIST_CANCELLED',
               'COURSE_CLOSED',
               'CREDIT_EXCEEDED',
               'TIME_CONFLICT'
             ) NOT NULL,
  message    VARCHAR(255) NOT NULL,
  read_at    DATETIME,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FOREIGN KEY (student_id) REFERENCES student(id),
  FOREIGN KEY (course_id)  REFERENCES course(id)
);
```

---

## 테이블 관계 요약

```
account ──── student ──── enrollment ──── course ──── course_schedule
               │               │                │
               └── professor   └── waitlist     └── department
                                    │
                                    └── course (professor_id FK)

student    ──── department
professor  ──── department
course     ──── department

notification → student
notification → course
```

---

## Redis Key 연계 요약

| Key | 초기화 시점 | 갱신 시점 | TTL |
|---|---|---|---|
| `enrollment:course:{id}` | 강의 등록 시 capacity로 초기화 | 수강신청/취소 시 DECR/INCR | 없음 |
| `waitlist:course:{id}` | 강의 등록 시 waitlist_limit으로 초기화 | 대기 등록/취소 시 DECR/INCR | 없음 |
| `schedule:student:{id}` | 첫 수강신청 시 RDS에서 조회 후 저장 | 수강신청/취소 시 추가/삭제 | 없음 |
| `credits:student:{id}` | 첫 수강신청 시 RDS에서 조회 후 저장 | 수강신청 완료 시 INCRBY, 취소 시 DECRBY | 없음 |
| `lock:course:{id}` | 대기자 처리 시작 시 세팅 | Scheduler가 직접 해제 | 없음 |
| `refresh:token:{accountId}` | 로그인 시 세팅 | 재로그인 시 덮어씌움 | 7일 |

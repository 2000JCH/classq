# 테스트 데이터

## 학생 계정 (6명) — 전원 1학년, 컴퓨터공학과

| 이름 | 이메일 | 비밀번호 |
|---|---|---|
| 김철수 | qqq123@naver.com | qqq12345! |
| 김유리 | zzz123@naver.com | zzz12345! |
| 신형만 | www123@naver.com | www12345! |
| 신짱구 | sss123@naver.com | sss12345! |
| 김훈이 | ddd123@naver.com | ddd12345! |
| 유맹구 | xxx123@naver.com | xxx12345! |

---

## 교수 계정 (1명) — 컴퓨터공학과

| 이름 | 이메일 | 비밀번호 |
|---|---|---|
| 김근태 | aaa123@naver.com | aaa12345! |

> 교수 계정은 가입 후 관리자 승인 필요

---

## 강의 (7개) — 전담 교수: 김근태, 전원 3학점, 대면(OFFLINE)

| 강의명 | 유형 | 수업방식 | 학년 | 정원 | 대기정원 | 요일 | 시간 |
|---|---|---|---|---|---|---|---|
| 데이터베이스 | 전공필수 (MAJOR_REQUIRED) | OFFLINE | 1~4 | 3 | 2 | 월 (MON) | 13:54~14:54 |
| 자료구조 | 전공필수 (MAJOR_REQUIRED) | OFFLINE | 1~4 | 2 | 2 | 월 (MON) | 13:54~15:54 |
| UI/UX | 전공선택 (MAJOR_ELECTIVE) | OFFLINE | 2~4 | 30 | 30 | 월 (MON) | 17:55~18:55 |
| 리더쉽 | 교양 (LIBERAL_ARTS) | OFFLINE | 1~4 | 30 | 30 | 월 (MON) | 17:56~19:56 |
| 음악의이해 | 교양 (LIBERAL_ARTS) | OFFLINE | 1~4 | 30 | 30 | 화 (TUE) | 16:57~19:57 |
| 교양이란 | 교양 (LIBERAL_ARTS) | **ONLINE** | 1~4 | 30 | 30 | 화 (TUE) | 17:59~18:59 |
| 경찰과도둑 | 교양/실습 (LIBERAL_ARTS + PRACTICE) | OFFLINE | 1~4 | 30 | 30 | 수 (WED) | 13:58~14:58 |

> - 전공 강의(데이터베이스, 자료구조, UI/UX): departmentId = 컴퓨터공학과
> - 교양 강의(리더쉽, 음악의이해, 교양이란, 경찰과도둑): departmentId = null
> - 경찰과도둑: classType = PRACTICE, 나머지: classType = THEORY

---

## Gatling 부하 테스트 계정 (자동 생성)

`./gradlew gatlingRun` 실행 시 `before()` 블록이 자동 생성한다. 직접 만들 필요 없음.

| 패턴 | 비밀번호 | 개수 |
|---|---|---|
| `loadtest{n}@test.com` (n = 1~300) | `Loadtest1!` | 300명 |

> - 이름: `부하테스트{n}`, 학과: 컴퓨터공학과(departmentId=1), 학년: 1
> - 이미 존재하면 409 → 무시하고 통과 (재실행 시 멱등)
> - 부하 테스트 대상 강의: course_id=3 (UI/UX, 정원 30명)
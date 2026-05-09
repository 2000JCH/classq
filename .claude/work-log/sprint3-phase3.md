# Sprint 3 — Phase 3. 사용자 · 학과

**기간:** 2026-05-07 ~ 2026-05-08  
**상태:** ⬜ 예정

---

## 목표

**배경**  
인증 이후 사용자가 본인의 정보를 조회·수정·탈퇴할 수 있어야 한다. 학과 목록은 수강신청 필터 등 다른 도메인에서도 참조하므로 먼저 제공해야 한다.

**문제 정의**  
현재 계정(account) 테이블만 존재하고, student / professor 프로필 정보와 학과 데이터를 외부에 노출하는 API가 없다.

**대상 사용자/화면**  
학생(마이페이지), 교수(프로필 페이지), 관리자(학과 관리)

**성공 기준 (완료 판정)**
- 기능: 학생/교수 본인 정보 조회·수정·탈퇴, 전체 학과 목록 조회 정상 동작
- 성능: 해당 없음
- 정확도: 탈퇴 시 `deleted_at` 세팅, 탈퇴 계정으로 로그인 시 401 반환
- 메모리: 해당 없음

---

## 스펙

**제공 인터페이스**
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/v1/students/me` | STUDENT | 내 정보 조회 |
| PUT | `/api/v1/students/me` | STUDENT | 내 정보 수정 |
| DELETE | `/api/v1/students/me` | STUDENT | 회원 탈퇴 (soft delete) |
| GET | `/api/v1/professors/me` | PROFESSOR | 내 정보 조회 |
| PUT | `/api/v1/professors/me` | PROFESSOR | 내 정보 수정 |
| GET | `/api/v1/departments` | 인증 필요 | 전체 학과 목록 조회 |

**핵심 기능**
- SecurityContext에서 accountId 추출 → 본인 정보만 조회/수정
- soft delete: 탈퇴 시 `account.deleted_at` + `student/professor.deleted_at` 세팅
- 탈퇴 계정 로그인 차단

---

## 구현

_작업 시작 후 채워넣기_

---

## 트러블슈팅

_작업 시작 후 채워넣기_

---

## 관련 문서
- `.claude/docs/api-design.md`
- `.claude/docs/db-design.md`

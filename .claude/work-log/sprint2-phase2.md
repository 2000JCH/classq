# Sprint 2 — Phase 2. 인증 (Auth)

**기간:** 2026-05-04 ~ 2026-05-06  
**상태:** ✅ 완료

---

## 목표

**배경**  
모든 API는 인증된 사용자만 접근할 수 있어야 한다. Stateless JWT 방식을 채택해 서버 세션 없이 토큰만으로 인증 상태를 관리한다. Refresh token은 Redis에만 저장해 로그아웃 및 무효화를 처리한다.

**문제 정의**  
Phase 1에서 `JwtUtil`과 `JwtFilter` 뼈대만 만들었고, 실제 회원가입/로그인/로그아웃/재발급 흐름과 토큰 보안 검증이 없었다.

**대상 사용자/화면**  
해당 없음 (API 레이어, 별도 화면 없음)

**성공 기준 (완료 판정)**
- 기능: 회원가입 → 로그인(토큰 발급) → 인증 필요 API 호출 → 토큰 재발급 → 로그아웃 흐름 정상 동작
- 성능: 해당 없음
- 정확도: access token으로만 일반 API 접근 가능, refresh token으로 일반 API 호출 시 401 반환
- 메모리: Redis에 `refresh:token:{accountId}` TTL 7일로 저장, 로그아웃 시 즉시 삭제

---

## 스펙

**처리 대상**
- Access Token: `typ=access`, 30분 유효, 일반 API 인증에 사용
- Refresh Token: `typ=refresh`, 7일 유효, Redis 저장, 재발급 전용

**제공 인터페이스**
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/v1/auth/signup` | 없음 | 회원가입 |
| POST | `/api/v1/auth/login` | 없음 | 로그인 — access token + refresh token 발급 |
| POST | `/api/v1/auth/logout` | 인증 필요 | 로그아웃 — Redis refresh token 삭제 |
| POST | `/api/v1/auth/refresh` | 없음 | access token 재발급 |

**핵심 기능**
- `JwtUtil`: 토큰 발급, 검증, 파싱, `typ` 클레임으로 토큰 종류 구별
- `JwtFilter`: Bearer 추출 → 유효성 검증 → access token 여부 확인 → SecurityContext 저장
- Redis: 로그인 시 refresh token 저장 (TTL 7일), 로그아웃 시 삭제, 재발급 시 일치 확인
- 회원가입 시 `ADMIN` 역할 직접 할당 차단
- 입력값 `@Valid` 검증

---

## 구현

### JwtUtil
- `createAccessToken()`: `typ=access`, `role` 클레임 포함
- `createRefreshToken()`: `typ=refresh` 클레임 포함
- `getTokenType()`: `typ` 클레임 추출 (내부 private 메서드) — `JwtException` / `IllegalArgumentException` catch 추가, 파싱 실패 시 `IllegalArgumentException` 재throw → JwtFilter에서 401 처리
- `isAccessToken()` / `isRefreshToken()`: 타입 구별
- `extractToken()`: `Authorization` 헤더에서 `Bearer ` 제거 후 순수 토큰 반환 — Bearer 추출 로직 JwtFilter에서 이곳으로 통합

### JwtFilter
- `resolveToken()`: `jwtUtil.extractToken()` 호출로 통일
- `validateToken() && isAccessToken()` 조건 모두 통과해야 SecurityContext 저장
- refresh token으로 일반 API 호출 시 인증 거부

### AccountController
- `refresh()`: `Authorization` 헤더 존재 여부 및 `Bearer ` 형식 검증 후 `extractToken()` 호출 — 형식 불일치 시 400 반환
- `signup()` / `login()`: `@Valid` 어노테이션 추가 — 요청 DTO 입력값 검증

### LoginRequestDto / SignupRequestDto
- `@NotBlank`, `@Email` 등 검증 어노테이션 추가 — 빈 값, 형식 오류 입력 차단

### AccountService
- `signup()`: `@Transactional` 추가, ADMIN 역할 차단 → 이메일 중복 확인 → 저장
- `login()`: `@Transactional` 추가, 이메일 조회 실패 / 비밀번호 불일치 모두 `LOGIN_FAILED` 반환 (에러 종류 노출 차단)
- `refresh()`: `@Transactional(readOnly = true)` 추가, `validateToken()` 명시적 호출 → `isRefreshToken()` 확인 → Redis 일치 확인 → 새 access token 발급
- `logout()`: SecurityContext의 accountId로 Redis 키 삭제

### ErrorCode
- `UNAUTHORIZED` / `LOGIN_FAILED` 분리 — 미인증(토큰 없음/만료)과 로그인 실패(자격증명 불일치)를 별도 코드로 관리

### application.yml
- `jwt.secret` 기본값 제거 — `JWT_SECRET` 환경변수 미설정 시 앱 시작 단계에서 실패하도록 강제

### Redis TTL
- `jwtUtil.getRefreshTokenExpiration()`으로 `application.yml` 값 그대로 사용

---

## 트러블슈팅

**1. Redis TTL 하드코딩**
- 상황: refresh token TTL을 코드에 직접 숫자(`604800000`)로 박아둠
- 원인: `application.yml` 값을 서비스 레이어에서 참조하는 방법을 고려하지 않음
- 해결: `JwtUtil.getRefreshTokenExpiration()` 메서드로 노출하고 `AccountService`에서 주입받아 사용

**2. 회원가입 시 ADMIN 역할 직접 할당 가능**
- 상황: 요청 body에 `role: ADMIN`을 넣으면 그대로 저장됨
- 원인: 서비스 레이어에 역할 검증 없음
- 해결: `signup()`에서 `Role.ADMIN` 체크 후 `FORBIDDEN` 예외 반환

**3. access / refresh 토큰 구별 불가 (보안)**
- 상황: 두 토큰이 같은 secretKey로 서명되어 서버가 종류를 구별할 수 없었음
- 원인: 토큰에 타입 정보가 없으니 `JwtFilter`가 refresh token도 통과시킴
- 해결: 토큰 생성 시 `typ` 클레임 추가 (`"access"` / `"refresh"`), `JwtFilter`에 `isAccessToken()` 검증, `AccountService.refresh()`에 `isRefreshToken()` 검증 추가

**4. refresh 엔드포인트 Bearer 형식 검증 누락**
- 상황: `/auth/refresh` 호출 시 `Authorization` 헤더 없거나 형식 불일치여도 NPE 또는 불명확한 에러 발생
- 원인: `AccountController`에 Bearer 형식 검증 없이 그대로 서비스로 전달
- 해결: 컨트롤러에서 헤더 존재 여부 및 `Bearer ` prefix 검증 후 400 반환

**5. 토큰 파싱 오류 시 500 에러**
- 상황: 위조·만료된 토큰으로 요청 시 500 Internal Server Error 반환
- 원인: `getTokenType()` 내부에서 `JwtException` / `IllegalArgumentException` 미처리
- 해결: catch 블록 추가, `IllegalArgumentException` 재throw → `JwtFilter`가 받아 401 처리

**6. 입력값 검증 누락**
- 상황: 회원가입/로그인 시 빈 값이나 형식에 맞지 않는 입력값이 그대로 처리됨
- 원인: 컨트롤러에 `@Valid`, DTO에 검증 어노테이션 없음
- 해결: `signup()` / `login()` 컨트롤러 메서드에 `@Valid` 추가, DTO 필드에 `@NotBlank` / `@Email` 등 검증 어노테이션 적용

**7. JWT 시크릿 기본값 보안 취약**
- 상황: `JWT_SECRET` 환경변수 미설정 시 `application.yml` 기본값으로 앱이 정상 구동됨
- 원인: `${JWT_SECRET:기본값}` 형식으로 설정되어 있어 환경변수 없어도 동작
- 해결: 기본값 제거 → 환경변수 미설정 시 앱 시작 실패로 운영 배포 시 누락 방지

---

## 관련 문서
- `.claude/docs/jwt.md`
- `.claude/docs/api-design.md`
- `.claude/docs/redis-design.md`
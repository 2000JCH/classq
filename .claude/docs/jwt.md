# ClassQ JWT 인증 설계

## 개요

ClassQ는 JWT(JSON Web Token) 기반 Stateless 인증을 사용한다. 세션을 서버에 저장하지 않으며, 모든 인증 상태는 토큰에 담긴다. Refresh token만 Redis에 저장하여 로그아웃 및 무효화를 처리한다.

---

## 토큰 종류

| 구분 | 수명 | 용도 | Redis 저장 | 클라이언트 저장 |
|---|---|---|---|---|
| Access Token | 30분 | 모든 인증 필요 API 호출 | X | 메모리 (Zustand store) |
| Refresh Token | 7일 | Access Token 재발급 전용 | O (`refresh:token:{accountId}`) | httpOnly Cookie |

두 토큰은 같은 `secretKey`로 서명하므로 `typ` 클레임으로 구분한다.

---

## 클라이언트 토큰 저장 전략

**Access Token → 메모리 (Zustand store)**
- JS에서 읽어 `Authorization: Bearer` 헤더에 직접 담아 전송
- 탭 닫거나 새로고침 시 사라짐 → 페이지 로드 시 refresh token으로 자동 재발급
- XSS 공격으로 탈취 불가 (localStorage와 달리 디스크에 저장되지 않음)

**Refresh Token → httpOnly Cookie**
- 백엔드가 로그인 응답 시 `Set-Cookie` 헤더로 내려줌
- JS에서 `document.cookie`로 접근 불가 → XSS 탈취 불가
- `/auth/refresh` 호출 시 브라우저가 자동으로 쿠키를 담아 전송
- Cookie 설정: `HttpOnly; Path=/api/v1/auth/refresh; SameSite=Strict; Max-Age=604800`

**프론트엔드 흐름:**
```
1. 로그인 → access token 메모리 저장, refresh token httpOnly cookie로 자동 저장
2. API 요청 → Authorization: Bearer {access token} 헤더 전송
3. 새로고침 → 메모리 초기화 → /auth/refresh 자동 호출 → access token 재발급
4. access token 만료(30분) → axios interceptor가 /auth/refresh 호출 → 재시도
5. refresh token 만료(7일) → 재발급 불가 → 로그인 페이지 이동
```

---

## 토큰 payload 구조

### Access Token
```json
{
  "sub": "1",
  "typ": "access",
  "role": "STUDENT",
  "iat": 1746518400,
  "exp": 1746520200
}
```

### Refresh Token
```json
{
  "sub": "1",
  "typ": "refresh",
  "iat": 1746518400,
  "exp": 1747123200
}
```

| 클레임 | 설명 |
|---|---|
| `sub` | accountId (account 테이블 PK) |
| `typ` | 토큰 종류 — `"access"` 또는 `"refresh"` |
| `role` | 권한 — `STUDENT`, `PROFESSOR`, `ADMIN` (access token에만 포함) |
| `iat` | 발급 시각 (Unix timestamp) |
| `exp` | 만료 시각 (Unix timestamp) |

---

## 환경 설정

`application.yml`에서 아래 값을 환경변수로 주입받는다.

```yaml
jwt:
  secret: ${JWT_SECRET:default-secret-key}
  access-token-expiration: ${JWT_ACCESS_EXPIRATION:1800000}    # 30분 (ms)
  refresh-token-expiration: ${JWT_REFRESH_EXPIRATION:604800000} # 7일 (ms)
```

---

## 구현 구조

```
요청 → SecurityConfig → JwtFilter → JwtUtil
                                 ↘ AccountService (refresh 엔드포인트)
```

### SecurityConfig

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**").permitAll()   // 인증 불필요
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
)
.addFilterBefore(new JwtFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class)
```

- `/api/v1/auth/**` — 인증 없이 접근 가능 (signup, login, refresh)
- `/api/v1/admin/**` — ADMIN 권한 필요
- 나머지 — 인증 필요

### JwtUtil

| 메서드 | 설명 |
|---|---|
| `createAccessToken(accountId, role)` | access token 생성 |
| `createRefreshToken(accountId)` | refresh token 생성 |
| `validateToken(token)` | 서명 유효성 + 만료 여부 검증 |
| `isAccessToken(token)` | `typ` 클레임이 `"access"`인지 확인 |
| `isRefreshToken(token)` | `typ` 클레임이 `"refresh"`인지 확인 |
| `getAccountId(token)` | `sub` 클레임에서 accountId 추출 |
| `getRole(token)` | `role` 클레임에서 권한 추출 |
| `extractToken(bearer)` | `Authorization` 헤더에서 토큰 추출 (`Bearer ` 제거) |

### JwtFilter

모든 요청에 실행되며, access token만 통과시킨다.

```
1. Authorization 헤더에서 Bearer 토큰 추출 (extractToken)
2. validateToken() → 서명 유효 + 만료 여부 확인
3. isAccessToken() → typ = "access" 확인
4. 통과 시 SecurityContextHolder에 인증 정보 저장
   - principal: accountId (Long)
   - authorities: ROLE_{role}
```

refresh token을 일반 API에 사용하면 `isAccessToken()`에서 false가 반환되어 인증이 거부된다.

---

## 인증 API 흐름

### 로그인 (`POST /api/v1/auth/login`)

```
1. email + password 검증 (account 테이블 조회)
2. access token 생성
3. refresh token 생성
4. Redis SET refresh:token:{accountId} = refreshToken (TTL 7일)
5. 응답:
   - response body: { accessToken }
   - Set-Cookie: refreshToken={refreshToken}; HttpOnly; Path=/api/v1/auth/refresh; SameSite=Strict; Max-Age=604800
```

### Access Token 재발급 (`POST /api/v1/auth/refresh`)

```
1. Cookie에서 refreshToken 추출 (브라우저가 자동으로 전송)
2. isRefreshToken() → typ = "refresh" 확인 (실패 시 INVALID_TOKEN)
3. getAccountId() → accountId 추출
4. Redis GET refresh:token:{accountId} → 저장된 토큰과 일치 확인 (불일치 시 UNAUTHORIZED)
5. account 조회 → 새 access token 생성
6. response body: { accessToken }  (refresh token은 기존 cookie 유지)
```

### 로그아웃 (`POST /api/v1/auth/logout`)

```
1. JwtFilter에서 SecurityContext에 저장된 accountId 조회
2. Redis DEL refresh:token:{accountId}
3. Set-Cookie: refreshToken=; Max-Age=0  (쿠키 즉시 삭제)
4. 이후 refresh token으로 재발급 시도 시 UNAUTHORIZED 응답
```

---

## Redis 연계

| Key | 초기화 | 만료 | TTL |
|---|---|---|---|
| `refresh:token:{accountId}` | 로그인 시 SET | 로그아웃 시 DEL | 7일 |

동일 계정 재로그인 시 덮어씌워 1계정 1토큰을 유지한다.
Redis 장애로 key가 없을 경우 재로그인을 요청한다.

---

## 에러 코드

| 코드 | HTTP | 상황 |
|---|---|---|
| `INVALID_TOKEN` | 401 | 위변조·형식 오류·잘못된 토큰 타입 (만료 포함, TOKEN_EXPIRED와 통합 처리) |
| `TOKEN_EXPIRED` | 401 | 만료된 토큰 — enum 정의됨, 현재는 INVALID_TOKEN으로 통합 반환 |
| `UNAUTHORIZED` | 401 | Redis 토큰 불일치, 계정 미존재 |
| `LOGIN_FAILED` | 401 | 이메일 또는 비밀번호 불일치 |
| `FORBIDDEN` | 403 | 권한 없는 접근 (ADMIN 역할 직접 할당 시도 등) |

---

## 관련 파일

| 파일 | 역할 |
|---|---|
| `global/auth/jwt/JwtUtil.java` | 토큰 발급 / 검증 / 파싱 |
| `global/auth/jwt/JwtFilter.java` | 요청별 토큰 검증 및 인증 처리 |
| `global/config/SecurityConfig.java` | 경로별 인증 규칙 설정 |
| `domain/account/service/AccountService.java` | 로그인 / 로그아웃 / refresh 비즈니스 로직 |
| `domain/account/controller/AccountController.java` | 인증 API 엔드포인트 |

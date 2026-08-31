 # ClassQ 프론트엔드 설계 결정

## 스택

- Vite + React + TypeScript
- 라우팅: React Router v6
- 상태 관리: Zustand (access token 저장)
- HTTP 클라이언트: axios
- UI: shadcn/ui + Tailwind CSS
- SSE: @microsoft/fetch-event-source

## 구현 범위

학생 / 교수 / 관리자 3개 역할 모두 구현

## 폴더 구조

```
src/
├── features/
│   ├── auth/
│   │   ├── pages/
│   │   ├── components/
│   │   └── hooks/
│   ├── course/
│   ├── enrollment/
│   ├── waitlist/
│   ├── notification/
│   ├── professor/
│   ├── student/
│   └── admin/
├── shared/
│   ├── api/         # axios 인스턴스, interceptor
│   ├── store/       # Zustand store (access token)
│   ├── components/  # 공통 UI 컴포넌트
│   └── hooks/
└── app/
    ├── router.tsx   # React Router 라우트 정의
    └── App.tsx
```

---

## CORS

개발 환경 허용 origin: `http://localhost:5173`

`withCredentials: true`가 필요하므로 `allowedOrigins("*")` 사용 불가 — 정확한 origin을 명시해야 한다.

---

## 토큰 관리

### Access Token — 메모리 저장 (Zustand store)

- 로그인 성공 시 response body의 `accessToken`을 Zustand store에 저장
- API 요청마다 `Authorization: Bearer {token}` 헤더에 포함
- 새로고침 시 사라짐 → 앱 진입 시 silent refresh로 복구

### Refresh Token — httpOnly Cookie

- 백엔드가 `Set-Cookie`로 내려줌 (`HttpOnly`, `Secure`, `Path=/api/v1/auth`)
- Path를 `/refresh` 한정이 아니라 `/api/v1/auth` 전체로 잡아 signup/login/logout 등 인증 관련 요청에서도 쿠키가 전송됨
- JS에서 접근 불가 → XSS 안전
- `/api/v1/auth/**` 요청 시 브라우저가 자동 전송

---

## Silent Refresh

앱 최초 진입 시 access token이 없으면 `/auth/refresh`를 자동 호출하여 복구한다. 이 호출 자체는 실패를 삼키고 `ready=true`만 세팅하며(`App.tsx`), 실제 로그인 페이지 이동은 인증이 필요한 라우트에 접근할 때 `PrivateRoute`가 accessToken 부재를 보고 처리한다 — 즉 "새로고침 복구 시도"와 "미인증 시 리다이렉트"가 서로 다른 컴포넌트 책임으로 분리되어 있다.

```
앱 진입
└── Zustand store에 accessToken 있음 → 정상 진행
└── 없음 → POST /auth/refresh 호출 (App.tsx)
    ├── 성공 → accessToken store에 저장 후 진행
    └── 실패 → 조용히 무시, ready=true만 세팅 (여기서 리다이렉트 안 함)

라우트 접근 시
└── PrivateRoute가 accessToken 없으면 → 로그인 페이지로 이동
```

---

## axios interceptor

401 응답 시 자동으로 토큰을 재발급하고 원래 요청을 재시도한다.

```
요청 → 401 응답
└── POST /auth/refresh 호출
    ├── 성공 → 새 accessToken store에 저장 → 원래 요청 재시도
    └── 실패 → 로그인 페이지로 이동
```

---

## SSE 연결

`@microsoft/fetch-event-source` 라이브러리를 사용한다.

기본 브라우저 EventSource API는 커스텀 헤더를 지원하지 않아 `Authorization` 헤더를 보낼 수 없다. 이 라이브러리는 `fetch()` 기반으로 SSE를 구현하여 커스텀 헤더 전송이 가능하다.

---

## SameSite 주의사항 (프로덕션)

현재 refresh token 쿠키는 `SameSite=Strict`로 설정되어 있다. 프론트와 백엔드가 같은 도메인이면 문제없지만, 도메인이 다를 경우 `SameSite=None; Secure`로 변경이 필요하다. 배포 시 도메인 확정 후 결정한다.
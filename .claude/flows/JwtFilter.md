# JwtFilter Flow

## 요청 처리 순서

```
요청 → SecurityConfig → JwtFilter → JwtUtil
```

- **JwtFilter**: JWT 관련 로직을 호출하는 흐름 제어 담당
- **JwtUtil**: 토큰 생성, 유효성 검사 등 토큰 자체에 대한 로직을 처리하는 메서드들 작성

---

## getAccountId() / getRole() 공통 파싱 흐름

두 메서드 모두 아래 체인으로 토큰을 파싱한다.

```java
Jwts.parser()
    .verifyWith(secretKey).build()
    .parseSignedClaims(token)
    .getPayload()
```

### Jwts.parser()
JWT 파싱을 담당하는 `JwtParserBuilder` 인스턴스를 생성한다.
토큰 문자열을 읽어 분석할 수 있는 엔진을 초기화한다.

### .verifyWith(secretKey).build()
- `.verifyWith(secretKey)`: 이 키를 검증 기준으로 삼겠다고 설정
- `.build()`: 검증 설정을 확정하여 파서(Parser)를 완성

### .parseSignedClaims(token)
인자로 받은 token 문자열을 해석하고 서명 및 유효성을 검증한다.
secretKey를 이용해 Signature를 계산하고, 전달받은 토큰의 Signature와 일치하는지 대조한다.

### .getPayload()
성공적으로 파싱된 토큰에서 사용자 ID, 권한, 발급 시간 등이 담긴 `Claims` 객체를 가져온다.
이 단계에 도달했다는 것은 해당 토큰이 신뢰할 수 있는 데이터임을 의미한다.
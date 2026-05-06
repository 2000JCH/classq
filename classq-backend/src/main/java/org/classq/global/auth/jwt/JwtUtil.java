package org.classq.global.auth.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;  // 마스터 키
    private final long accessTokenExpiration;   //만료 시간
    private final long refreshTokenExpiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public long getRefreshTokenExpiration(){
        return refreshTokenExpiration;
    }

    // access token 생성
    public String createAccessToken(Long accountId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(accountId)) // 유저 ID
                .claim("typ", "access")
                .claim("role", role)    // 커스텀 데이터 (권한 삽입)
                .issuedAt(now)  // 발급 시간
                .expiration(new Date(now.getTime() + accessTokenExpiration))    // 만료 시간
                .signWith(secretKey)    //위의 발급 받을것들을 secretKey를 이용해서 해싱
                .compact(); // 압축
    }

    // refresh token 생성
    public String createRefreshToken(Long accountId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .claim("typ", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    //유효성 검증 (클라이언트가 가져온 토큰이 진짜인지, 유효기간이 지나진 않았는지 확인)
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 사용자의 id값을 추출하는 메소드
    public Long getAccountId(String token) {
        String subject = Jwts.parser()
                .verifyWith(secretKey).build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    }

    // 사용자의 권한(role)을 추출하는 메소드
    public String getRole(String token) {
        return Jwts.parser()
                .verifyWith(secretKey).build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    // 받은 토큰 타입 종류 확인
    private String getTokenType(String token) {
        return Jwts.parser()
                .verifyWith(secretKey).build()
                .parseSignedClaims(token)
                .getPayload()
                .get("typ", String.class);  //String.class 값만 반환 -> 반환값 : access, refresh
    }

    public boolean isAccessToken(String token) {
        return "access".equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTokenType(token));
    }
}
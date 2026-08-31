package org.classq.global.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    // AuthenticationEntryPoint가 TOKEN_EXPIRED와 그 외 인증 실패를 구분할 때 참조하는 요청 속성 키
    public static final String TOKEN_EXPIRED_ATTRIBUTE = "classq.tokenExpired";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null) {
            if (jwtUtil.validateToken(token) && jwtUtil.isAccessToken(token)) {
                Long accountId = jwtUtil.getAccountId(token);
                String role = jwtUtil.getRole(token);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        accountId,  //principal (유저의 id값 등 사용자 정보)
                        null,   // Credentials (jwt 비밀번호를 이미 검증했으니 null)
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)) //Authorities (ROLE_USER, ROLE_ADMIN 권한 리스트)
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);   //사용자 정보 저장
            } else if (jwtUtil.isTokenExpired(token)) {
                request.setAttribute(TOKEN_EXPIRED_ATTRIBUTE, true);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        return jwtUtil.extractToken(request.getHeader("Authorization"));
    }
}
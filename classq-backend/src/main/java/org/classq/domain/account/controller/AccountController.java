package org.classq.domain.account.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.classq.domain.account.dto.LoginRequestDto;
import org.classq.domain.account.dto.SignupRequestDto;
import org.classq.domain.account.dto.TokenResponseDto;
import org.classq.domain.account.service.AccountService;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AccountController {

    private final AccountService accountService;

    //회원가입
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@RequestBody @Valid SignupRequestDto request) {
        accountService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    //로그인
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@RequestBody @Valid LoginRequestDto request) {
        TokenResponseDto response = accountService.login(request);
        return ResponseEntity.ok(response);
    }

    //로그아웃
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        Long accountId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        accountService.logout(accountId);
        return ResponseEntity.ok().build();
    }

    /**
     * 액세스 토큰 재발급
     *  1. Authorization: Bearer 로 보내는 게 업계 표준
     *  2. Spring Security 연동 — Spring Security의 JWT 필터가 기본적으로 Authorization 헤더를 읽도록 설계되어 있음
     *  3. 클라이언트 라이브러리 호환 — axios, fetch 등 대부분의 HTTP 클라이언트가 Bearer 토큰 처리를 기본 지원
     *
     *  http헤더에는 Authorization 포함한 다양한 값이 있다. (Content-Type, Host, Cookie등)
     *  Authorization: Bearer eyJhbGciOiJIUzI1NiJ9... 값을 가져와서 앞 7글자 제거 순수한 jwt 값만 남김
     *
     * **/
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refresh(@RequestHeader("Authorization") String bearer) {
        if (!bearer.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        String refreshToken = bearer.substring(7);  //앞 7글자 제거
        return ResponseEntity.ok(accountService.refresh(refreshToken));
    }
}

package org.classq.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.account.dto.LoginRequestDto;
import org.classq.domain.account.dto.SignupRequestDto;
import org.classq.domain.account.dto.TokenResponseDto;
import org.classq.domain.account.entity.Account;
import org.classq.domain.account.entity.Role;
import org.classq.domain.account.repository.AccountRepository;
import org.classq.global.auth.jwt.JwtUtil;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    //회원가입
    public void signup(SignupRequestDto request) {

        if (request.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        //중복체크
        if (accountRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        } else {
            Account account = Account.builder()
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(request.getRole())
                    .build();
            accountRepository.save(account);
        }
    }

    //로그인
    public TokenResponseDto login(LoginRequestDto request) {

        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = jwtUtil.createAccessToken(account.getId(), account.getRole().name());
        String refreshToken = jwtUtil.createRefreshToken(account.getId());

        //레디스 refresh token 저장 (로그인 할 때마다 새 리프레시 토큰을 생성해서 Redis에 덮어쓴다)
        redisTemplate.opsForValue().set(
                "refresh:token:" +account.getId(),
                refreshToken,
                jwtUtil.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS
        );

        return new TokenResponseDto(accessToken, refreshToken);
    }

    //로그아웃
    public void logout(Long accountId) {
        redisTemplate.delete("refresh:token:" + accountId);
    }

    //액새스 토큰 재발급 (리프레시 토큰을 이용해서 새로운 액세스 토큰 재발급)
    public TokenResponseDto refresh(String refreshToken) {
        Long accountId = jwtUtil.getAccountId(refreshToken);

        String saved = redisTemplate.opsForValue().get("refresh:token:" + accountId);
        if (saved == null || !saved.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        String newAccessToken = jwtUtil.createAccessToken(accountId, account.getRole().name());

        return new TokenResponseDto(newAccessToken, refreshToken);
    }
}




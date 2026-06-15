package org.classq.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.account.dto.LoginRequestDto;
import org.classq.domain.account.dto.SignupRequestDto;
import org.classq.domain.account.dto.TokenResponseDto;
import org.classq.domain.account.entity.Account;
import org.classq.domain.account.entity.AccountStatus;
import org.classq.domain.account.entity.Role;
import org.classq.domain.account.repository.AccountRepository;
import org.classq.domain.department.entity.Department;
import org.classq.domain.department.repository.DepartmentRepository;
import org.classq.domain.professor.entity.Professor;
import org.classq.domain.professor.repository.ProfessorRepository;
import org.classq.domain.student.entity.Student;
import org.classq.domain.student.repository.StudentRepository;
import org.classq.global.auth.jwt.JwtUtil;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final StudentRepository studentRepository;
    private final ProfessorRepository professorRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    //회원가입
    @Transactional
    public void signup(SignupRequestDto request) {

        if (request.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        //중복체크
        if (accountRepository.findByEmailAndDeletedAtIsNull(request.getEmail()).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));

        AccountStatus status = request.getRole() == Role.PROFESSOR ? AccountStatus.PENDING : AccountStatus.ACTIVE;

        Account account = Account.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(status)
                .build();
        accountRepository.save(account);

        if (request.getRole() == Role.STUDENT) {
            if (request.getGrade() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            studentRepository.save(Student.builder()
                    .account(account)
                    .department(department)
                    .name(request.getName())
                    .grade(request.getGrade())
                    .build());
        } else if (request.getRole() == Role.PROFESSOR) {
            professorRepository.save(Professor.builder()
                    .account(account)
                    .department(department)
                    .name(request.getName())
                    .build());
        }
    }

    //로그인
    @Transactional
    public TokenResponseDto login(LoginRequestDto request) {

        Account account = accountRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(()->new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (account.getStatus() == AccountStatus.PENDING) {
            throw new BusinessException(ErrorCode.ACCOUNT_PENDING);
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
    @Transactional
    public TokenResponseDto refresh(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Long accountId = jwtUtil.getAccountId(refreshToken);

        String saved = redisTemplate.opsForValue().get("refresh:token:" + accountId);
        if (saved == null || !saved.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Account account = accountRepository.findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        String newAccessToken = jwtUtil.createAccessToken(accountId, account.getRole().name());

        return new TokenResponseDto(newAccessToken, refreshToken);
    }
}




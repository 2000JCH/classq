package org.classq.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    //인증
    EMAIL_ALREADY_EXISTS(409, "이미 존재하는 이메일입니다."),
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(401, "만료된 토큰입니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    LOGIN_FAILED(401, "이메일 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    STUDENT_NOT_FOUND(404,"학생을 찾을 수 없습니다."),
    PROFESSOR_NOT_FOUND(404,"교수를 찾을 수 없습니다."),
    COURSE_NOT_FOUND(404,"강의를 찾을 수 없습니다."),
    DEPARTMENT_NOT_FOUND(404,"학과를 찾을 수 없습니다."),
    ENROLLMENT_NOT_FOUND(404, "수강신청 내역을 찾을 수 없습니다."),
    WAITLIST_NOT_FOUND(404, "대기 내역을 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(404, "알림을 찾을 수 없습니다."),
    WAITLIST_INVALID_STATUS(409, "취소할 수 없는 대기 상태입니다."),
    WAITLIST_EXPIRED(409, "수락 가능 시간이 만료되었습니다."),

    //서버
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),

    //수강신청
    ENROLLMENT_CLOSED(409, "수강 신청이 마감되었습니다."),
    WAITLIST_CLOSED(409, "대기 신청이 마감되었습니다."),
    ENROLLMENT_LOCKED(403, "현재 대기자 처리 중입니다."),
    CREDIT_EXCEEDED(400, "학점이 초과되었습니다."),
    TIME_CONFLICT(400, "시간표가 중복됩니다."),
    DUPLICATE_ENROLLMENT(409, "이미 신청한 강의입니다."),
    DUPLICATE_WAITLIST(409, "이미 대기 신청한 강의입니다."),
    INVALID_INPUT(400, "요청 입력값이 올바르지 않습니다.");

    private final int status;
    private final String message;
}

/*
  - 비즈니스 규칙 위반 → 400 BAD_REQUEST
  - 인증 없음 → 401 UNAUTHORIZED
  - 권한 없음 → 403 FORBIDDEN
  - 리소스 없음 → 404 NOT_FOUND
  - 중복/충돌 → 409 CONFLICT
*/
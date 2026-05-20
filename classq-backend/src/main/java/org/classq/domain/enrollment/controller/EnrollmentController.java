package org.classq.domain.enrollment.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.enrollment.dto.EnrollmentRequestDto;
import org.classq.domain.enrollment.dto.EnrollmentResponseDto;
import org.classq.domain.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    // 내 수강신청 목록 조회
    @GetMapping("/me")
    public ResponseEntity<List<EnrollmentResponseDto>> getMyEnrollments(@AuthenticationPrincipal Long accountId) {
        return ResponseEntity.ok(enrollmentService.getMyEnrollments(accountId));
    }

    // 수강 신청
    @PostMapping
    public ResponseEntity<Void> enroll(@AuthenticationPrincipal Long accountId, @Valid @RequestBody EnrollmentRequestDto request) {
        enrollmentService.enroll(accountId, request.getCourseId());
        return ResponseEntity.status(201).build();
    }
}
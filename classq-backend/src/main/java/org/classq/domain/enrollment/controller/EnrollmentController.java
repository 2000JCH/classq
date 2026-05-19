package org.classq.domain.enrollment.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.enrollment.dto.EnrollmentRequestDto;
import org.classq.domain.enrollment.service.EnrollmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    // 수강 신청
    @PostMapping
    public ResponseEntity<Void> enroll(@AuthenticationPrincipal Long accountId, @RequestBody EnrollmentRequestDto request) {
        enrollmentService.enroll(accountId, request.getCourseId());
        return ResponseEntity.status(201).build();
    }
}
package org.classq.domain.student.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.student.dto.StudentResponseDto;
import org.classq.domain.student.service.StudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService studentService;

    // 내정보 조회
    @GetMapping("/me")
    public ResponseEntity<StudentResponseDto> getMe(@AuthenticationPrincipal Long accountId) {
        return ResponseEntity.ok(studentService.me(accountId));
    }
}

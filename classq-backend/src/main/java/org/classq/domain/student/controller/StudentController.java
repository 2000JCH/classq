package org.classq.domain.student.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.student.dto.StudentRequestDto;
import org.classq.domain.student.dto.StudentResponseDto;
import org.classq.domain.student.service.StudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService studentService;

    // 내 정보 조회
    @GetMapping("/me")
    public ResponseEntity<StudentResponseDto> getMe(@AuthenticationPrincipal Long accountId) {
        return ResponseEntity.ok(studentService.getMe(accountId));
    }

    // 내 정보 수정
    @PutMapping("/me")
    public ResponseEntity<StudentResponseDto> updateMe(@AuthenticationPrincipal Long accountId, @RequestBody StudentRequestDto request) {
        return ResponseEntity.ok(studentService.updateMe(accountId, request));
    }

    // 회원 탈퇴
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Long accountId) {
        studentService.deleteMe(accountId);
        return ResponseEntity.noContent().build();
    }
}

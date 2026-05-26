package org.classq.domain.admin.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.admin.dto.AdminStudentResponseDto;
import org.classq.domain.admin.service.AdminStudentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminStudentController {

    private final AdminStudentService adminStudentService;

    // 전체 학생 목록 조회
    @GetMapping("/students")
    public ResponseEntity<Page<AdminStudentResponseDto>> getStudents(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(adminStudentService.getStudents(pageable));
    }

    // 특정 학생 강제 탈퇴
    @DeleteMapping("/students/{studentId}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long studentId) {
        adminStudentService.deleteStudent(studentId);
        return ResponseEntity.noContent().build();
    }
}
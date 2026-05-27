package org.classq.domain.admin.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.admin.dto.AdminEnrollmentResponseDto;
import org.classq.domain.admin.dto.AdminWaitlistResponseDto;
import org.classq.domain.admin.dto.EnrollmentStatsDto;
import org.classq.domain.admin.service.AdminCourseService;
import org.classq.domain.course.dto.CourseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminCourseController {

    private final AdminCourseService adminCourseService;

    // 전체 강의 목록 조회
    @GetMapping("/courses")
    public ResponseEntity<Page<CourseDto>> getCourses(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(adminCourseService.getCourses(pageable));
    }

    // 수강신청 현황 조회
    @GetMapping("/courses/{courseId}/enrollments")
    public ResponseEntity<Page<AdminEnrollmentResponseDto>> getEnrollments(
            @PathVariable Long courseId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(adminCourseService.getEnrollments(courseId, pageable));
    }

    // 특정 강의 대기자 명단 조회
    @GetMapping("/courses/{courseId}/waitlists")
    public ResponseEntity<Page<AdminWaitlistResponseDto>> getWaitlists(
            @PathVariable Long courseId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(adminCourseService.getWaitlists(courseId, pageable));
    }

    // 수강신청 현황 통계
    @GetMapping("/stats/enrollments")
    public ResponseEntity<EnrollmentStatsDto> getEnrollmentStats() {
        return ResponseEntity.ok(adminCourseService.getEnrollmentStats());
    }

    // 특정 강의 강제 폐강
    @DeleteMapping("/courses/{courseId}")
    public ResponseEntity<Void> closeCourse(@PathVariable Long courseId) {
        adminCourseService.closeCourse(courseId);
        return ResponseEntity.noContent().build();
    }
}
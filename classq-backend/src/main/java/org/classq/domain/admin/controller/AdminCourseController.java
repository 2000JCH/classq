package org.classq.domain.admin.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.admin.service.AdminCourseService;
import org.classq.domain.course.dto.CourseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
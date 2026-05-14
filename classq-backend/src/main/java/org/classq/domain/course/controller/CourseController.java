package org.classq.domain.course.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.dto.CourseCreateRequestDto;
import org.classq.domain.course.dto.CourseDetailDto;
import org.classq.domain.course.dto.CourseDto;
import org.classq.domain.course.dto.CourseScheduleDto;
import org.classq.domain.course.entity.enums.ClassMode;
import org.classq.domain.course.entity.enums.ClassType;
import org.classq.domain.course.entity.enums.CourseType;
import org.classq.domain.course.service.CourseScheduleService;
import org.classq.domain.course.service.CourseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courseService;
    private final CourseScheduleService courseScheduleService;

    // 강의 목록 조회
    @GetMapping
    public ResponseEntity<Page<CourseDto>> getCourses(
            @RequestParam(required = false) CourseType courseType,
            @RequestParam(required = false) ClassType classType,
            @RequestParam(required = false) ClassMode classMode,
            @RequestParam(required = false) Long departmentId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(courseService.getCourses(courseType, classType, classMode, departmentId, pageable));
    }

    // 강의 상세 조회
    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailDto> getCourseDetail(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getCourseDetail(courseId));
    }

    // 강의 시간표 조회
    @GetMapping("/{courseId}/schedules")
    public ResponseEntity<List<CourseScheduleDto>> getCourseSchedule(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseScheduleService.getCourseSchedules(courseId));
    }

    // 강의 등록
    @PostMapping
    public ResponseEntity<Long> createCourse(@AuthenticationPrincipal Long accountId, @RequestBody CourseCreateRequestDto request) {
        return ResponseEntity.status(201).body(courseService.createCourse(accountId, request));
    }

}
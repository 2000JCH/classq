package org.classq.domain.course.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.dto.CourseDto;
import org.classq.domain.course.entity.enums.ClassMode;
import org.classq.domain.course.entity.enums.ClassType;
import org.classq.domain.course.entity.enums.CourseType;
import org.classq.domain.course.service.CourseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courseService;

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
}
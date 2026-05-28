package org.classq.domain.department.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.department.dto.DepartmentDto;
import org.classq.domain.department.service.DepartmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    // 전체 학과 조회
    @GetMapping()
    public ResponseEntity<List<DepartmentDto>> findAll() {
        return ResponseEntity.ok(departmentService.findAll());
    }

}

package org.classq.domain.department.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.department.dto.DepartmentDto;
import org.classq.domain.department.entity.Department;
import org.classq.domain.department.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<DepartmentDto> findAll() {
        List<Department> departments = departmentRepository.findAll();

        return departments.stream()
                .map(d -> new DepartmentDto(d.getId(), d.getName()))
                .collect(Collectors.toList());

    }

}

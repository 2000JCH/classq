package org.classq.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.admin.dto.AdminStudentResponseDto;
import org.classq.domain.student.repository.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStudentService {

    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public Page<AdminStudentResponseDto> getStudents(Pageable pageable) {
        return studentRepository.findAllActiveStudents(pageable)
                .map(AdminStudentResponseDto::from);
    }
}
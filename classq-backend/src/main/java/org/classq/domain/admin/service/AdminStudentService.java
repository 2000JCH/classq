package org.classq.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.admin.dto.AdminStudentResponseDto;
import org.classq.domain.student.entity.Student;
import org.classq.domain.student.repository.StudentRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStudentService {

    private final StudentRepository studentRepository;

    // 전체 학생 목록 조회
    @Transactional(readOnly = true)
    public Page<AdminStudentResponseDto> getStudents(Pageable pageable) {
        return studentRepository.findAllActiveStudents(pageable)
                .map(AdminStudentResponseDto::from);
    }

    // 특정 학생 강제 탈퇴
    @Transactional
    public void deleteStudent(Long studentId) {
        Student student = studentRepository.findByIdAndDeletedAtIsNull(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        student.delete();
        student.getAccount().delete();
    }
}
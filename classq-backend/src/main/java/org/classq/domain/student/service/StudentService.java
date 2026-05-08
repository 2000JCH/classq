package org.classq.domain.student.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.student.dto.StudentRequestDto;
import org.classq.domain.student.dto.StudentResponseDto;
import org.classq.domain.student.entity.Student;
import org.classq.domain.student.repository.StudentRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    // 내(학생) 정보 조회
    @Transactional(readOnly = true)
    public StudentResponseDto getMe(Long accountId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
        return new StudentResponseDto(student.getAccount().getEmail(), student.getName(), student.getGrade());
    }

    // 내(학생) 정보 수정
    @Transactional
    public StudentResponseDto updateMe(Long accountId, StudentRequestDto request) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        //값을 안 보내면 기존 값 유지
        student.update(
                request.getName() != null ? request.getName() : student.getName(),
                request.getGrade() != null ? request.getGrade() : student.getGrade()
        );
        return new StudentResponseDto(student.getAccount().getEmail(), student.getName(), student.getGrade());
    }

    // 회원 탈퇴
    @Transactional
    public void deleteMe(Long accountId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        student.delete();
        student.getAccount().delete();
    }
}

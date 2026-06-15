package org.classq.domain.professor.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.professor.dto.ProfessorRequestDto;
import org.classq.domain.professor.dto.ProfessorResponseDto;
import org.classq.domain.professor.entity.Professor;
import org.classq.domain.professor.repository.ProfessorRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfessorService {

    private final ProfessorRepository professorRepository;

    // 내 정보 조회
    @Transactional(readOnly = true)
    public ProfessorResponseDto getMe(Long accountId) {
        Professor professor = professorRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFESSOR_NOT_FOUND));

        return new ProfessorResponseDto(professor.getName(), professor.getAccount().getEmail(),
                professor.getDepartment().getName()
        );
    }

    //내 정보 수정
    @Transactional
    public ProfessorResponseDto updateMe(Long accountId, ProfessorRequestDto request) {
        Professor professor = professorRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFESSOR_NOT_FOUND));

        professor.update(
                request.getName() != null ? request.getName() : professor.getName()
        );

        return new ProfessorResponseDto(professor.getName(), professor.getAccount().getEmail(),
                professor.getDepartment().getName());
    }
}

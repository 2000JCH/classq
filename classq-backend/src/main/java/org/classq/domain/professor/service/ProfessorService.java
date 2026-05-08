package org.classq.domain.professor.service;

import lombok.RequiredArgsConstructor;
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
}

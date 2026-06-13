package org.classq.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.account.entity.Account;
import org.classq.domain.account.entity.AccountStatus;
import org.classq.domain.account.repository.AccountRepository;
import org.classq.domain.admin.dto.PendingProfessorDto;
import org.classq.domain.professor.repository.ProfessorRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AccountRepository accountRepository;
    private final ProfessorRepository professorRepository;

    @Transactional(readOnly = true)
    public List<PendingProfessorDto> getPendingProfessors() {
        return professorRepository.findAllByAccountStatus(AccountStatus.PENDING)
                .stream()
                .map(PendingProfessorDto::from)
                .toList();
    }

    @Transactional
    public void approveProfessor(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        account.approve();
    }
}

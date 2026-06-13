package org.classq.domain.professor.repository;

import org.classq.domain.account.entity.AccountStatus;
import org.classq.domain.professor.entity.Professor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {

    // 교수 정보 조회-수정 ( soft delete된 교수 제외 )
    Optional<Professor> findByAccountIdAndDeletedAtIsNull(Long accountId);

    // 승인 대기 교수 목록
    @Query("SELECT p FROM Professor p JOIN FETCH p.account a JOIN FETCH p.department WHERE a.status = :status AND p.deletedAt IS NULL")
    List<Professor> findAllByAccountStatus(AccountStatus status);
}

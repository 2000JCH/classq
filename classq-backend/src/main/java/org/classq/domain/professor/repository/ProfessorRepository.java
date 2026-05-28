package org.classq.domain.professor.repository;

import org.classq.domain.professor.entity.Professor;
import org.classq.domain.student.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {

    // 교수 정보 조회-수정 ( soft delete된 교수 제외 )
    Optional<Professor> findByAccountIdAndDeletedAtIsNull(Long accountId);
}

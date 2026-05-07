package org.classq.domain.student.repository;

import org.classq.domain.student.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    // soft delete된 학생 제외
    Optional<Student> findByAccountIdAndDeletedAtIsNull(Long accountId);

}

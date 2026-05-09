package org.classq.domain.student.repository;

import org.classq.domain.student.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    // 내(학생) 정보 조회-수정 ( soft delete된 학생 제외 )
    Optional<Student> findByAccountIdAndDeletedAtIsNull(Long accountId);

}

package org.classq.domain.student.repository;

import org.classq.domain.student.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    // 내(학생) 정보 조회-수정 ( soft delete된 학생 제외 )
    Optional<Student> findByAccountIdAndDeletedAtIsNull(Long accountId);

    @Query(value = "SELECT s FROM Student s JOIN FETCH s.account JOIN FETCH s.department WHERE s.deletedAt IS NULL",
           countQuery = "SELECT COUNT(s) FROM Student s WHERE s.deletedAt IS NULL")
    Page<Student> findAllActiveStudents(Pageable pageable);

}

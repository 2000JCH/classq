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

    Optional<Student> findByIdAndDeletedAtIsNull(Long id);

    @Query(value = "SELECT s FROM Student s JOIN FETCH s.account JOIN FETCH s.department WHERE s.deletedAt IS NULL",    //이메일, 학과명 조회
           countQuery = "SELECT COUNT(s) FROM Student s WHERE s.deletedAt IS NULL") // 전체 개수 조회
    Page<Student> findAllActiveStudents(Pageable pageable);

}

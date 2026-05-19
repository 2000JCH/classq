package org.classq.domain.enrollment.repository;

import org.classq.domain.enrollment.entity.Enrollment;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    int countByCourse_IdAndEnrollmentStatus(Long courseId, EnrollmentStatus status);

    // 수강신청한 총 학점 계산
    @Query("SELECT COALESCE(SUM(e.course.credits), 0) FROM Enrollment e " +
            "WHERE e.student.id = :studentId AND e.enrollmentStatus = 'COMPLETED' AND e.deletedAt IS NULL")
    int sumCreditsByStudentId(@Param("studentId") Long studentId);
}
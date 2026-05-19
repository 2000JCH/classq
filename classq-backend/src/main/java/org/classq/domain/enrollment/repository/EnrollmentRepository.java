package org.classq.domain.enrollment.repository;

import org.classq.domain.enrollment.dto.EnrollmentResponseDto;
import org.classq.domain.enrollment.entity.Enrollment;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    int countByCourse_IdAndEnrollmentStatus(Long courseId, EnrollmentStatus status);

    @Query("SELECT COALESCE(SUM(e.course.credits), 0) FROM Enrollment e " +
            "WHERE e.student.id = :studentId AND e.enrollmentStatus = 'COMPLETED' AND e.deletedAt IS NULL")
    long sumCreditsByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT new org.classq.domain.enrollment.dto.EnrollmentResponseDto(" +
            "e.id, e.course.id, e.course.name, e.course.credits, e.course.professor.name, e.enrollmentStatus) " +
            "FROM Enrollment e " +
            "WHERE e.student.id = :studentId AND e.deletedAt IS NULL")
    List<EnrollmentResponseDto> findMyEnrollments(@Param("studentId") Long studentId);
}
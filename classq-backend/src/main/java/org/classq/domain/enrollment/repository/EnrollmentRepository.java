package org.classq.domain.enrollment.repository;

import org.classq.domain.enrollment.entity.Enrollment;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    int countByCourse_IdAndEnrollmentStatus(Long courseId, EnrollmentStatus status);
}
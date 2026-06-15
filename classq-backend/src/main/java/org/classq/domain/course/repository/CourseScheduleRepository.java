package org.classq.domain.course.repository;

import org.classq.domain.course.entity.CourseSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, Long> {

    List<CourseSchedule> findByCourseId(Long courseId);

    @Query("SELECT cs FROM CourseSchedule cs WHERE cs.course.id IN " +
            "(SELECT e.course.id FROM Enrollment e WHERE e.student.id = :studentId " +
            "AND e.enrollmentStatus = 'COMPLETED' AND e.deletedAt IS NULL)")
    List<CourseSchedule> findActiveSchedulesByStudentId(@Param("studentId") Long studentId);
}

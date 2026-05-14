package org.classq.domain.course.repository;

import org.classq.domain.course.entity.CourseSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, Long> {

    List<CourseSchedule> findByCourseId(Long courseId);
}

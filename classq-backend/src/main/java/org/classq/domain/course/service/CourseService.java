package org.classq.domain.course.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.dto.CourseDto;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.entity.enums.ClassMode;
import org.classq.domain.course.entity.enums.ClassType;
import org.classq.domain.course.entity.enums.CourseStatus;
import org.classq.domain.course.entity.enums.CourseType;
import org.classq.domain.course.repository.CourseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public Page<CourseDto> getCourses(CourseType courseType, ClassType classType, ClassMode classMode, Long departmentId, Pageable pageable) {
        Specification<Course> spec = Specification
                .where(notDeleted())
                .and(statusActive())
                .and(eqCourseType(courseType))
                .and(eqClassType(classType))
                .and(eqClassMode(classMode))
                .and(eqDepartmentId(departmentId));

        return courseRepository.findAll(spec, pageable).map(this::toDto);
    }

    private CourseDto toDto(Course course) {
        return new CourseDto(
                course.getId(),
                course.getName(),
                course.getProfessor().getName(),
                course.getDepartment() != null ? course.getDepartment().getName() : null,
                course.getCourseType(),
                course.getClassType(),
                course.getClassMode(),
                course.getCredits(),
                course.getCapacity(),
                course.getMinGrade(),
                course.getMaxGrade(),
                course.getCourseStatus()
        );
    }

    private Specification<Course> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private Specification<Course> statusActive() {
        return (root, query, cb) -> cb.equal(root.get("courseStatus"), CourseStatus.ACTIVE);
    }

    private Specification<Course> eqCourseType(CourseType courseType) {
        return courseType == null ? null : (root, query, cb) -> cb.equal(root.get("courseType"), courseType);
    }

    private Specification<Course> eqClassType(ClassType classType) {
        return classType == null ? null : (root, query, cb) -> cb.equal(root.get("classType"), classType);
    }

    private Specification<Course> eqClassMode(ClassMode classMode) {
        return classMode == null ? null : (root, query, cb) -> cb.equal(root.get("classMode"), classMode);
    }

    private Specification<Course> eqDepartmentId(Long departmentId) {
        return departmentId == null ? null : (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
    }
}
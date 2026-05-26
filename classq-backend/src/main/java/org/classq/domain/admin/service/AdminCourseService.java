package org.classq.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.dto.CourseDto;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.repository.CourseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCourseService {

    private final CourseRepository courseRepository;

    // 전체 강의 목록 조회 (CLOSED 포함, soft delete 제외)
    @Transactional(readOnly = true)
    public Page<CourseDto> getCourses(Pageable pageable) {
        // spec는 조건만 정의되서 들어감
        Specification<Course> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));

        /**
         *
         * spec -> WHERE deleted_at IS NULL
         * pageable -> LIMIT 20 OFFSET 0
         *
         * SELECT * FROM course
         * WHERE deleted_at IS NULL
         * LIMIT 20 OFFSET 0
         *
         * 반환: Page<Course>
         * Page<Course> 삭제 안된 Course들을 20개씩 넘겨 받으면 하나씩 꺼내서 toDto()에 넘김
         * */
        return courseRepository.findAll(spec, pageable).map(this::toDto);
    }

    // Page<CourseDto>에 담아서 반환
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
}
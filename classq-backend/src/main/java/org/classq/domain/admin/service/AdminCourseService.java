package org.classq.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.admin.dto.AdminEnrollmentResponseDto;
import org.classq.domain.admin.dto.AdminWaitlistResponseDto;
import org.classq.domain.admin.dto.EnrollmentStatsDto;
import org.classq.domain.course.dto.CourseDto;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.classq.domain.waitlist.repository.WaitlistRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminCourseService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;

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

    // 수강신청 현황 조회
    @Transactional(readOnly = true)
    public Page<AdminEnrollmentResponseDto> getEnrollments(Long courseId, Pageable pageable) {
        courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        return enrollmentRepository.findByCourse_IdAndDeletedAtIsNull(courseId, pageable)
                .map(AdminEnrollmentResponseDto::from);
    }

    // 특정 강의 대기자 명단 조회
    @Transactional(readOnly = true)
    public Page<AdminWaitlistResponseDto> getWaitlists(Long courseId, Pageable pageable) {
        courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        return waitlistRepository.findByCourse_IdAndDeletedAtIsNullOrderByRankAsc(courseId, pageable)
                .map(AdminWaitlistResponseDto::from);
    }

    // 수강신청 현황 통계
    @Transactional(readOnly = true)
    public EnrollmentStatsDto getEnrollmentStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return new EnrollmentStatsDto(
                enrollmentRepository.countToday(startOfDay),
                enrollmentRepository.countTotal(),
                enrollmentRepository.countCancelled()
        );
    }

    // 특정 강의 강제 폐강
    @Transactional
    public void closeCourse(Long courseId) {
        // 삭제안된 강의만 조회
        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        course.close();
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
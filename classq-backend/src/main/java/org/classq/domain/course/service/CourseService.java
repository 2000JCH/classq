package org.classq.domain.course.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.dto.CourseCreateRequestDto;
import org.classq.domain.course.dto.CourseDetailDto;
import org.classq.domain.course.dto.CourseDto;
import org.classq.domain.course.dto.CourseUpdateRequestDto;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.entity.CourseSchedule;
import org.classq.domain.course.entity.enums.ClassMode;
import org.classq.domain.course.entity.enums.ClassType;
import org.classq.domain.course.entity.enums.CourseStatus;
import org.classq.domain.course.entity.enums.CourseType;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.course.repository.CourseScheduleRepository;
import org.classq.domain.department.entity.Department;
import org.classq.domain.department.repository.DepartmentRepository;
import org.classq.domain.notification.entity.Notification;
import org.classq.domain.notification.entity.NotificationType;
import org.classq.domain.notification.repository.NotificationRepository;
import org.classq.domain.notification.service.SseEmitterService;
import org.classq.domain.professor.entity.Professor;
import org.classq.domain.professor.repository.ProfessorRepository;
import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.classq.domain.waitlist.repository.WaitlistRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final ProfessorRepository professorRepository;
    private final DepartmentRepository departmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final NotificationRepository notificationRepository;
    private final SseEmitterService sseEmitterService;
    private final RedisTemplate<String, String> redisTemplate;

    // 강의 목록 조회
    @Transactional(readOnly = true)
    public Page<CourseDto> getCourses(CourseType courseType, ClassType classType, ClassMode classMode, Long departmentId, Pageable pageable) {
        Specification<Course> spec = notDeleted()
                .and(statusActive())
                .and(eqCourseType(courseType))
                .and(eqClassType(classType))
                .and(eqClassMode(classMode))
                .and(eqDepartmentId(departmentId));

        return courseRepository.findAll(spec, pageable).map(this::toDto);
    }

    // 강의 상세 조회
    @Transactional(readOnly = true)
    public CourseDetailDto getCourseDetail(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        return new CourseDetailDto(
                course.getId(),
                course.getName(),
                course.getProfessor().getName(),
                course.getDepartment() != null ? course.getDepartment().getName() : null,
                course.getCourseType(),
                course.getClassType(),
                course.getClassMode(),
                course.getCredits(),
                course.getCapacity(),
                getRemainingCapacity(course),
                course.getWaitlistLimit(),
                getWaitlistRemaining(course),
                course.getMinGrade(),
                course.getMaxGrade(),
                course.getCourseStatus()
        );
    }

    // 강의 등록
    @Transactional
    public Long createCourse(Long accountId, CourseCreateRequestDto request) {
        Professor professor = professorRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFESSOR_NOT_FOUND));

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        }

        Course course = Course.builder()
                .professor(professor)
                .department(department)
                .name(request.getName())
                .courseType(request.getCourseType())
                .classType(request.getClassType())
                .classMode(request.getClassMode())
                .credits(request.getCredits())
                .capacity(request.getCapacity())
                .waitlistLimit(request.getWaitlistLimit())
                .minGrade(request.getMinGrade())
                .maxGrade(request.getMaxGrade())
                .build();

        courseRepository.save(course);

        if (request.getSchedules() != null) {
            for (CourseCreateRequestDto.ScheduleRequest s : request.getSchedules()) {
                CourseSchedule schedule = CourseSchedule.builder()
                        .course(course)
                        .courseScheduleDay(s.getDay())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .build();
                courseScheduleRepository.save(schedule);
            }
        }

        long savedCourseId = course.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.opsForValue().set("enrollment:course:" + savedCourseId, String.valueOf(request.getCapacity()));
                redisTemplate.opsForValue().set("waitlist:course:" + savedCourseId, String.valueOf(request.getWaitlistLimit()));
            }
        });

        return course.getId();
    }

    // 강의 수정
    @Transactional
    public void updateCourse(Long accountId, Long courseId, CourseUpdateRequestDto request) {
        Professor professor = professorRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFESSOR_NOT_FOUND));

        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (!course.getProfessor().getId().equals(professor.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        }

        int oldCapacity = course.getCapacity();
        int oldWaitlistLimit = course.getWaitlistLimit();

        course.update(request.getName(), request.getClassMode(), department, request.getCapacity(), request.getWaitlistLimit(), request.getMinGrade(), request.getMaxGrade());

        // 정원/대기 정원 변경 시 Redis 잔여석 조정 — DB 커밋 후 반영해 롤백 시 불일치 방지
        int capacityDiff = request.getCapacity() - oldCapacity;
        int waitlistDiff = request.getWaitlistLimit() - oldWaitlistLimit;
        String enrollmentKey = "enrollment:course:" + courseId;
        String waitlistKey = "waitlist:course:" + courseId;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                String enrollmentVal = redisTemplate.opsForValue().get(enrollmentKey);
                if (enrollmentVal != null) {
                    try {
                        int newRemaining = Math.max(0, Integer.parseInt(enrollmentVal) + capacityDiff);
                        redisTemplate.opsForValue().set(enrollmentKey, String.valueOf(newRemaining));
                    } catch (NumberFormatException ignored) {}
                }

                String waitlistVal = redisTemplate.opsForValue().get(waitlistKey);
                if (waitlistVal != null) {
                    try {
                        int newRemaining = Math.max(0, Integer.parseInt(waitlistVal) + waitlistDiff);
                        redisTemplate.opsForValue().set(waitlistKey, String.valueOf(newRemaining));
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        // 교수가 정원 증가 시 대기자 알림
        if (request.getCapacity() > oldCapacity) {
            boolean alreadyNotified = waitlistRepository
                    .findFirstByCourse_IdAndWaitlistStatusAndDeletedAtIsNullOrderByRankAsc(courseId, WaitlistStatus.NOTIFIED)
                    .isPresent();

            if (!alreadyNotified) {
                // ZSET에서 rank 1번 waitlistId 조회 후 행 단위 비관적 락
                String nextIdStr = Optional.ofNullable(
                        redisTemplate.opsForZSet().range("waitlist:zset:course:" + courseId, 0, 0)
                ).flatMap(set -> set.stream().findFirst()).orElse(null);

                Optional<Waitlist> nextOpt = nextIdStr == null ? Optional.empty()
                        : waitlistRepository.findByIdForUpdate(Long.valueOf(nextIdStr))
                                .filter(w -> w.getWaitlistStatus() == WaitlistStatus.WAITING && w.getDeletedAt() == null);

                if (nextOpt.isPresent()) {
                    Waitlist waitlist = nextOpt.get();
                    waitlist.notified();

                    Notification notification = notificationRepository.save(
                            Notification.builder()
                                    .student(waitlist.getStudent())
                                    .course(waitlist.getCourse())
                                    .notificationType(NotificationType.WAITLIST_AVAILABLE)
                                    .message("수강 신청 자리가 생겼습니다. 10분 내에 수락해 주세요.")
                                    .build()
                    );

                    Long studentId = waitlist.getStudent().getId();

                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            redisTemplate.opsForValue().set("lock:course:" + courseId, "1", 15, TimeUnit.MINUTES);
                            sseEmitterService.send(studentId, notification);
                        }
                    });
                }
            }
        }
    }

    // 강의 폐강
    @Transactional
    public void closeCourse(Long accountId, Long courseId) {
        Professor professor = professorRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFESSOR_NOT_FOUND));

        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (!course.getProfessor().getId().equals(professor.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        course.close();
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
                getRemainingCapacity(course),
                course.getWaitlistLimit(),
                getWaitlistRemaining(course),
                course.getMinGrade(),
                course.getMaxGrade(),
                course.getCourseStatus()
        );
    }

    private int getWaitlistRemaining(Course course) {
        String value = redisTemplate.opsForValue().get("waitlist:course:" + course.getId());
        if (value != null) {
            try {
                return Math.max(0, Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return course.getWaitlistLimit();
            }
        }
        return course.getWaitlistLimit();
    }

    private int getRemainingCapacity(Course course) {
        String value = redisTemplate.opsForValue().get("enrollment:course:" + course.getId());
        if (value != null) {
            try {
                return Math.max(0, Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return course.getCapacity();
            }
        }
        return course.getCapacity();
    }

    // Specification 문법 / JPA가 제공하는 동적 쿼리 방식
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
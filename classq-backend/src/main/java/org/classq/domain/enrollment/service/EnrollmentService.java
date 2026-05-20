package org.classq.domain.enrollment.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.entity.CourseSchedule;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.course.repository.CourseScheduleRepository;
import org.classq.domain.enrollment.dto.EnrollmentResponseDto;
import org.classq.domain.enrollment.entity.Enrollment;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.classq.domain.enrollment.producer.dto.EnrollmentCancelEvent;
import org.classq.domain.enrollment.producer.dto.EnrollmentEvent;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.classq.domain.student.entity.Student;
import org.classq.domain.student.repository.StudentRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;



@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 내 수강신청 목록 조회
    public List<EnrollmentResponseDto> getMyEnrollments(Long accountId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
        return enrollmentRepository.findMyEnrollments(student.getId());
    }

    // 수강 신청
    public void enroll(Long accountId, Long courseId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        Long studentId = student.getId();

        // 1. 대기자 처리 중 여부
        if (Boolean.TRUE.equals(redisTemplate.hasKey("lock:course:" + courseId))) {
            throw new BusinessException(ErrorCode.ENROLLMENT_LOCKED);
        }

        // 2. 시간표 중복 체크
        List<CourseSchedule> newSchedules = courseScheduleRepository.findByCourseId(courseId);
        checkScheduleConflict(studentId, newSchedules);

        // 3. 19학점 초과 체크
        String creditsKey = "credits:student:" + studentId;
        String creditsCached = redisTemplate.opsForValue().get(creditsKey);
        if (creditsCached == null) {
            long loaded = enrollmentRepository.sumCreditsByStudentId(studentId);
            creditsCached = String.valueOf(loaded);
            redisTemplate.opsForValue().set(creditsKey, creditsCached);
        }
        int currentCredits = Integer.parseInt(creditsCached);
        if (currentCredits + course.getCredits() > 19) {
            throw new BusinessException(ErrorCode.CREDIT_EXCEEDED);
        }

        // 4. 정원 차감 (음수면 롤백)
        Long remaining = redisTemplate.opsForValue().decrement("enrollment:course:" + courseId);
        if (remaining == null || remaining < 0) {
            redisTemplate.opsForValue().increment("enrollment:course:" + courseId);
            throw new BusinessException(ErrorCode.ENROLLMENT_CLOSED);
        }

        // 5. Kafka 발행
        List<EnrollmentEvent.ScheduleEntry> scheduleEntries = newSchedules.stream()
                .map(s -> new EnrollmentEvent.ScheduleEntry(
                        s.getCourseScheduleDay().name(),
                        s.getStartTime(),
                        s.getEndTime()
                ))
                .toList();

        try {
            kafkaTemplate.send("enrollment-events", String.valueOf(studentId),
                    new EnrollmentEvent(studentId, courseId, course.getCredits(), scheduleEntries)).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            redisTemplate.opsForValue().increment("enrollment:course:" + courseId);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 수강신청 취소
    public void cancel(Long accountId, Long enrollmentId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Enrollment enrollment = enrollmentRepository
                .findByIdAndStudent_IdAndEnrollmentStatusAndDeletedAtIsNull(
                        enrollmentId, student.getId(), EnrollmentStatus.COMPLETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        Long studentId = student.getId();
        Long courseId = enrollment.getCourse().getId();
        int credits = enrollment.getCourse().getCredits();

        List<CourseSchedule> schedules = courseScheduleRepository.findByCourseId(courseId);

        // 1. 정원 복구 (키 없으면 DB 기준으로 재초기화)
        String enrollmentKey = "enrollment:course:" + courseId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(enrollmentKey))) {
            redisTemplate.opsForValue().increment(enrollmentKey);
        } else {
            int enrolled = enrollmentRepository.countByCourse_IdAndEnrollmentStatus(courseId, EnrollmentStatus.COMPLETED);
            int remaining = enrollment.getCourse().getCapacity() - enrolled + 1;
            redisTemplate.opsForValue().set(enrollmentKey, String.valueOf(remaining));
        }

        // 2. 시간표 캐시 삭제 (hasKey 결과를 미리 저장해 롤백에 재사용)
        String scheduleKey = "schedule:student:" + studentId;
        boolean scheduleKeyExists = Boolean.TRUE.equals(redisTemplate.hasKey(scheduleKey));
        if (scheduleKeyExists) {
            for (CourseSchedule s : schedules) {
                redisTemplate.opsForSet().remove(scheduleKey,
                        s.getCourseScheduleDay().name() + "|" + s.getStartTime() + "|" + s.getEndTime());
            }
        }

        // 3. 학점 캐시 차감
        String creditsKey = "credits:student:" + studentId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(creditsKey))) {
            redisTemplate.opsForValue().decrement(creditsKey, credits);
        }

        // 4. Kafka 발행
        List<EnrollmentCancelEvent.ScheduleEntry> scheduleEntries = schedules.stream()
                .map(s -> new EnrollmentCancelEvent.ScheduleEntry(
                        s.getCourseScheduleDay().name(),
                        s.getStartTime(),
                        s.getEndTime()
                ))
                .toList();

        try {
            kafkaTemplate.send("enrollment-cancel-events", String.valueOf(studentId),
                    new EnrollmentCancelEvent(enrollmentId, studentId, courseId, credits, scheduleEntries)).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Kafka 실패 시 Redis 롤백
            redisTemplate.opsForValue().decrement(enrollmentKey);
            if (scheduleKeyExists) {
                for (CourseSchedule s : schedules) {
                    redisTemplate.opsForSet().add(scheduleKey,
                            s.getCourseScheduleDay().name() + "|" + s.getStartTime() + "|" + s.getEndTime());
                }
            }
            if (Boolean.TRUE.equals(redisTemplate.hasKey(creditsKey))) {
                redisTemplate.opsForValue().increment(creditsKey, credits);
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 시간표 체크
    private void checkScheduleConflict(Long studentId, List<CourseSchedule> newSchedules) {
        String scheduleKey = "schedule:student:" + studentId;

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(scheduleKey))) {
            List<CourseSchedule> existing = courseScheduleRepository.findActiveSchedulesByStudentId(studentId);
            for (CourseSchedule s : existing) {
                redisTemplate.opsForSet().add(scheduleKey,
                        s.getCourseScheduleDay().name() + "|" + s.getStartTime() + "|" + s.getEndTime());
            }
        }

        Set<String> cached = redisTemplate.opsForSet().members(scheduleKey);
        if (cached == null || cached.isEmpty()) return;

        for (CourseSchedule newSchedule : newSchedules) {
            for (String entry : cached) {
                String[] parts = entry.split("\\|");
                if (!parts[0].equals(newSchedule.getCourseScheduleDay().name())) continue;
                LocalTime existStart = LocalTime.parse(parts[1]);
                LocalTime existEnd = LocalTime.parse(parts[2]);
                // 경계만 맞닿는 경우(startTime == existEnd 등)는 통과
                if (newSchedule.getStartTime().isBefore(existEnd) && newSchedule.getEndTime().isAfter(existStart)) {
                    throw new BusinessException(ErrorCode.TIME_CONFLICT);
                }
            }
        }
    }
}
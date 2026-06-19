package org.classq.domain.enrollment.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.entity.CourseSchedule;
import org.classq.domain.course.repository.CourseScheduleRepository;
import org.classq.domain.enrollment.dto.EnrollmentResponseDto;
import org.classq.domain.enrollment.entity.Enrollment;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.classq.domain.enrollment.producer.dto.EnrollmentCancelEvent;
import org.classq.domain.enrollment.producer.dto.EnrollmentEvent;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.classq.domain.student.repository.StudentRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final StudentRepository studentRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public List<EnrollmentResponseDto> getMyEnrollments(Long accountId) {
        String studentIdStr = redisTemplate.opsForValue().get("student:account:" + accountId);
        Long studentId;
        if (studentIdStr != null) {
            studentId = Long.valueOf(studentIdStr);
        } else {
            studentId = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND))
                    .getId();
        }
        return enrollmentRepository.findMyEnrollments(studentId);
    }

    public void enroll(Long accountId, Long courseId) {
        // studentId — Redis 캐시 (login/signup 시 세팅)
        String studentIdStr = redisTemplate.opsForValue().get("student:account:" + accountId);
        if (studentIdStr == null) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        Long studentId = Long.valueOf(studentIdStr);

        // 강의 학점 — Redis 캐시 (DataInitializer 시 세팅)
        String creditsStr = redisTemplate.opsForValue().get("course:" + courseId + ":credits");
        if (creditsStr == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        int courseCredits = Integer.parseInt(creditsStr);

        // 1. 대기자 처리 중 락 확인
        if (Boolean.TRUE.equals(redisTemplate.hasKey("lock:course:" + courseId))) {
            throw new BusinessException(ErrorCode.ENROLLMENT_LOCKED);
        }

        // 2. 시간표 중복 체크 (동일 강의 재신청 시 타임슬롯 충돌로 자연스럽게 차단)
        Set<String> newCourseSchedules = redisTemplate.opsForSet().members("course:" + courseId + ":schedules");
        if (newCourseSchedules == null) newCourseSchedules = Set.of();
        checkScheduleConflict(studentId, newCourseSchedules);

        // 3. 19학점 초과 체크
        String creditsKey = "credits:student:" + studentId;
        String creditsCached = redisTemplate.opsForValue().get(creditsKey);
        if (creditsCached == null) {
            long loaded = enrollmentRepository.sumCreditsByStudentId(studentId);
            creditsCached = String.valueOf(loaded);
            redisTemplate.opsForValue().set(creditsKey, creditsCached);
        }
        if (Integer.parseInt(creditsCached) + courseCredits > 19) {
            throw new BusinessException(ErrorCode.CREDIT_EXCEEDED);
        }

        // 4. 정원 차감
        Long remaining = redisTemplate.opsForValue().decrement("enrollment:course:" + courseId);
        if (remaining == null || remaining < 0) {
            redisTemplate.opsForValue().increment("enrollment:course:" + courseId);
            throw new BusinessException(ErrorCode.ENROLLMENT_CLOSED);
        }

        // 5. Kafka 발행
        List<EnrollmentEvent.ScheduleEntry> scheduleEntries = newCourseSchedules.stream()
                .map(entry -> {
                    String[] parts = entry.split("\\|");
                    return new EnrollmentEvent.ScheduleEntry(parts[0], LocalTime.parse(parts[1]), LocalTime.parse(parts[2]));
                })
                .toList();

        try {
            kafkaTemplate.send("enrollment-events", String.valueOf(studentId),
                    new EnrollmentEvent(studentId, courseId, courseCredits, scheduleEntries)).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            redisTemplate.opsForValue().increment("enrollment:course:" + courseId);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void cancel(Long accountId, Long enrollmentId) {
        String studentIdStr = redisTemplate.opsForValue().get("student:account:" + accountId);
        if (studentIdStr == null) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        Long studentId = Long.valueOf(studentIdStr);

        Enrollment enrollment = enrollmentRepository
                .findByIdAndStudent_IdAndEnrollmentStatusAndDeletedAtIsNull(
                        enrollmentId, studentId, EnrollmentStatus.COMPLETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        Long courseId = enrollment.getCourse().getId();

        String creditsStr = redisTemplate.opsForValue().get("course:" + courseId + ":credits");
        int credits = creditsStr != null ? Integer.parseInt(creditsStr) : enrollment.getCourse().getCredits();

        Set<String> scheduleEntries = redisTemplate.opsForSet().members("course:" + courseId + ":schedules");
        if (scheduleEntries == null) scheduleEntries = Set.of();

        // 1. 정원 복구
        String enrollmentKey = "enrollment:course:" + courseId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(enrollmentKey))) {
            redisTemplate.opsForValue().increment(enrollmentKey);
        } else {
            int enrolled = enrollmentRepository.countByCourse_IdAndEnrollmentStatusAndDeletedAtIsNull(courseId, EnrollmentStatus.COMPLETED);
            int remaining = enrollment.getCourse().getCapacity() - enrolled + 1;
            redisTemplate.opsForValue().set(enrollmentKey, String.valueOf(remaining));
        }

        // 2. 시간표 캐시에서 제거
        String scheduleKey = "schedule:student:" + studentId;
        boolean scheduleKeyExists = Boolean.TRUE.equals(redisTemplate.hasKey(scheduleKey));
        if (scheduleKeyExists) {
            for (String entry : scheduleEntries) {
                redisTemplate.opsForSet().remove(scheduleKey, entry);
            }
        }

        // 3. 학점 캐시 차감
        String creditsKey = "credits:student:" + studentId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(creditsKey))) {
            redisTemplate.opsForValue().decrement(creditsKey, credits);
        }

        // 4. Kafka 발행
        List<EnrollmentCancelEvent.ScheduleEntry> kafkaScheduleEntries = scheduleEntries.stream()
                .map(entry -> {
                    String[] parts = entry.split("\\|");
                    return new EnrollmentCancelEvent.ScheduleEntry(parts[0], LocalTime.parse(parts[1]), LocalTime.parse(parts[2]));
                })
                .toList();

        final Set<String> finalScheduleEntries = scheduleEntries;
        try {
            kafkaTemplate.send("enrollment-cancel-events", String.valueOf(studentId),
                    new EnrollmentCancelEvent(enrollmentId, studentId, courseId, credits, kafkaScheduleEntries)).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            redisTemplate.opsForValue().decrement(enrollmentKey);
            if (scheduleKeyExists) {
                for (String entry : finalScheduleEntries) {
                    redisTemplate.opsForSet().add(scheduleKey, entry);
                }
            }
            if (Boolean.TRUE.equals(redisTemplate.hasKey(creditsKey))) {
                redisTemplate.opsForValue().increment(creditsKey, credits);
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public void enrollFromWaitlist(Long studentId, Long courseId) {
        String creditsStr = redisTemplate.opsForValue().get("course:" + courseId + ":credits");
        if (creditsStr == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        int courseCredits = Integer.parseInt(creditsStr);

        Set<String> newCourseSchedules = redisTemplate.opsForSet().members("course:" + courseId + ":schedules");
        if (newCourseSchedules == null) newCourseSchedules = Set.of();
        checkScheduleConflict(studentId, newCourseSchedules);

        String creditsKey = "credits:student:" + studentId;
        String creditsCached = redisTemplate.opsForValue().get(creditsKey);
        if (creditsCached == null) {
            long loaded = enrollmentRepository.sumCreditsByStudentId(studentId);
            creditsCached = String.valueOf(loaded);
            redisTemplate.opsForValue().set(creditsKey, creditsCached);
        }
        if (Integer.parseInt(creditsCached) + courseCredits > 19) {
            throw new BusinessException(ErrorCode.CREDIT_EXCEEDED);
        }

        Long remaining = redisTemplate.opsForValue().decrement("enrollment:course:" + courseId);
        if (remaining == null || remaining < 0) {
            redisTemplate.opsForValue().increment("enrollment:course:" + courseId);
            throw new BusinessException(ErrorCode.ENROLLMENT_CLOSED);
        }

        List<EnrollmentEvent.ScheduleEntry> scheduleEntries = newCourseSchedules.stream()
                .map(entry -> {
                    String[] parts = entry.split("\\|");
                    return new EnrollmentEvent.ScheduleEntry(parts[0], LocalTime.parse(parts[1]), LocalTime.parse(parts[2]));
                })
                .toList();

        try {
            kafkaTemplate.send("enrollment-events", String.valueOf(studentId),
                    new EnrollmentEvent(studentId, courseId, courseCredits, scheduleEntries)).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            redisTemplate.opsForValue().increment("enrollment:course:" + courseId);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        redisTemplate.delete("lock:course:" + courseId);
        redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
    }

    private void checkScheduleConflict(Long studentId, Set<String> newScheduleEntries) {
        String scheduleKey = "schedule:student:" + studentId;

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(scheduleKey))) {
            List<CourseSchedule> existing = courseScheduleRepository.findActiveSchedulesByStudentId(studentId);
            for (CourseSchedule s : existing) {
                redisTemplate.opsForSet().add(scheduleKey,
                        s.getCourseScheduleDay().name() + "|" + s.getStartTime() + "|" + s.getEndTime());
            }
        }

        if (newScheduleEntries.isEmpty()) return;

        Set<String> cached = redisTemplate.opsForSet().members(scheduleKey);
        if (cached == null || cached.isEmpty()) return;

        for (String newEntry : newScheduleEntries) {
            String[] newParts = newEntry.split("\\|");
            String newDay = newParts[0];
            LocalTime newStart = LocalTime.parse(newParts[1]);
            LocalTime newEnd = LocalTime.parse(newParts[2]);

            for (String existingEntry : cached) {
                String[] parts = existingEntry.split("\\|");
                if (!parts[0].equals(newDay)) continue;
                LocalTime existStart = LocalTime.parse(parts[1]);
                LocalTime existEnd = LocalTime.parse(parts[2]);
                if (newStart.isBefore(existEnd) && newEnd.isAfter(existStart)) {
                    throw new BusinessException(ErrorCode.TIME_CONFLICT);
                }
            }
        }
    }
}

package org.classq.domain.enrollment.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.entity.CourseSchedule;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.course.repository.CourseScheduleRepository;
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

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

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
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(creditsKey))) {
            int loaded = enrollmentRepository.sumCreditsByStudentId(studentId);
            redisTemplate.opsForValue().set(creditsKey, String.valueOf(loaded));
        }
        int currentCredits = Integer.parseInt(redisTemplate.opsForValue().get(creditsKey));
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

        kafkaTemplate.send("enrollment-events", String.valueOf(studentId),
                new EnrollmentEvent(studentId, courseId, course.getCredits(), scheduleEntries));
    }

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
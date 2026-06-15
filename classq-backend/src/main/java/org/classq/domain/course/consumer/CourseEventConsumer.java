package org.classq.domain.course.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.classq.domain.course.consumer.dto.CourseDebeziumPayloadDto;
import org.classq.domain.course.consumer.dto.CourseSnapshotDto;
import org.classq.domain.course.consumer.dto.DebeziumCourseEventDto;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.entity.CourseSchedule;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.course.repository.CourseScheduleRepository;
import org.classq.domain.enrollment.entity.Enrollment;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.classq.domain.notification.entity.Notification;
import org.classq.domain.notification.entity.NotificationType;
import org.classq.domain.notification.repository.NotificationRepository;
import org.classq.domain.notification.service.SseEmitterService;
import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.classq.domain.waitlist.repository.WaitlistRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventConsumer {

    private final RedisTemplate<String, String> redisTemplate;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final CourseRepository courseRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final NotificationRepository notificationRepository;
    private final SseEmitterService sseEmitterService;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "course-events", groupId = "enrollment-processor")
    public void consume(String message) {
        DebeziumCourseEventDto event;
        try {
            event = objectMapper.readValue(message, DebeziumCourseEventDto.class);
        } catch (Exception e) {
            log.error("course-events 파싱 실패: {}", e.getMessage(), e);
            return;
        }

        CourseDebeziumPayloadDto payload = event.getPayload();
        if (payload == null || !"u".equals(payload.getOp()) || payload.getAfter() == null) return;

        CourseSnapshotDto after = payload.getAfter();
        CourseSnapshotDto before = payload.getBefore();

        if (after.getId() == null) {
            log.warn("course-events: after.id가 null입니다.");
            return;
        }

        long courseId = after.getId();

        // 폐강
        if ("CLOSED".equals(after.getStatus())) {
            handleCourseClosed(courseId);
            return;
        }

        // 정원 변경
        if (before != null && before.getCapacity() != null && after.getCapacity() != null
                && !before.getCapacity().equals(after.getCapacity())) {
            int enrolled = enrollmentRepository.countByCourse_IdAndEnrollmentStatusAndDeletedAtIsNull(courseId, EnrollmentStatus.COMPLETED);
            int remaining = Math.max(0, after.getCapacity() - enrolled);
            redisTemplate.opsForValue().set("enrollment:course:" + courseId, String.valueOf(remaining));
            log.info("정원 변경 반영 - courseId: {}, 잔여: {}", courseId, remaining);
        }
    }

    private void handleCourseClosed(long courseId) {
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) return;

        List<CourseSchedule> schedules = courseScheduleRepository.findByCourseId(courseId);
        String closedMessage = "'" + course.getName() + "' 강의가 폐강되었습니다.";
        List<Map.Entry<Long, Notification>> sseQueue = new ArrayList<>();
        List<Long> enrolledStudentIds = new ArrayList<>();

        // 1. 수강 중인 학생 처리 — soft-delete + 알림 저장
        List<Enrollment> enrollments = enrollmentRepository
                .findByCourse_IdAndEnrollmentStatusAndDeletedAtIsNull(courseId, EnrollmentStatus.COMPLETED);

        for (Enrollment enrollment : enrollments) {
            Long studentId = enrollment.getStudent().getId();
            enrollment.delete();
            enrolledStudentIds.add(studentId);

            Notification notification = notificationRepository.save(Notification.builder()
                    .student(enrollment.getStudent())
                    .course(course)
                    .notificationType(NotificationType.COURSE_CLOSED)
                    .message(closedMessage)
                    .build());
            sseQueue.add(new AbstractMap.SimpleEntry<>(studentId, notification));
        }

        // 2. 대기 중인 학생 처리 — soft-delete + 알림 저장
        List<Waitlist> waitlists = waitlistRepository.findByCourse_IdAndWaitlistStatusInAndDeletedAtIsNull(
                courseId, List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED));

        for (Waitlist waitlist : waitlists) {
            Long studentId = waitlist.getStudent().getId();
            waitlist.delete();

            Notification notification = notificationRepository.save(Notification.builder()
                    .student(waitlist.getStudent())
                    .course(course)
                    .notificationType(NotificationType.COURSE_CLOSED)
                    .message(closedMessage)
                    .build());
            sseQueue.add(new AbstractMap.SimpleEntry<>(studentId, notification));
        }

        // 3. DB 커밋 후 Redis 작업 + SSE 발송
        int credits = course.getCredits();
        List<String> scheduleEntries = schedules.stream()
                .map(s -> s.getCourseScheduleDay().name() + "|" + s.getStartTime() + "|" + s.getEndTime())
                .toList();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long studentId : enrolledStudentIds) {
                    String creditsKey = "credits:student:" + studentId;
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(creditsKey))) {
                        redisTemplate.opsForValue().decrement(creditsKey, credits);
                    }
                    String scheduleKey = "schedule:student:" + studentId;
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(scheduleKey))) {
                        for (String entry : scheduleEntries) {
                            redisTemplate.opsForSet().remove(scheduleKey, entry);
                        }
                    }
                }
                redisTemplate.delete("lock:course:" + courseId);
                redisTemplate.delete("enrollment:course:" + courseId);
                redisTemplate.delete("waitlist:course:" + courseId);
                for (Map.Entry<Long, Notification> entry : sseQueue) {
                    sseEmitterService.send(entry.getKey(), entry.getValue());
                }
            }
        });

        log.info("폐강 처리 완료 - courseId: {}, 수강자: {}명, 대기자: {}명",
                courseId, enrollments.size(), waitlists.size());
    }
}
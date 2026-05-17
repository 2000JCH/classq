package org.classq.domain.course.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.classq.domain.course.consumer.dto.CourseDebeziumPayloadDto;
import org.classq.domain.course.consumer.dto.CourseSnapshotDto;
import org.classq.domain.course.consumer.dto.DebeziumCourseEventDto;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventConsumer {

    private final RedisTemplate<String, String> redisTemplate;
    private final EnrollmentRepository enrollmentRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "course-events", groupId = "enrollment-processor")
    public void consume(String message) {
        try {
            DebeziumCourseEventDto event = objectMapper.readValue(message, DebeziumCourseEventDto.class);
            CourseDebeziumPayloadDto payload = event.getPayload();

            if (payload == null || !"u".equals(payload.getOp()) || payload.getAfter() == null) {
                return;
            }

            CourseSnapshotDto after = payload.getAfter();
            CourseSnapshotDto before = payload.getBefore();

            if (after.getId() == null) {
                log.warn("course-events: after.id가 null입니다.");
                return;
            }

            long courseId = after.getId();

            if ("CLOSED".equals(after.getStatus())) {
                redisTemplate.delete("lock:course:" + courseId);
                redisTemplate.delete("enrollment:course:" + courseId);
                redisTemplate.delete("waitlist:course:" + courseId);
                log.info("폐강 처리 완료 - courseId: {}", courseId);
                return;
            }

            if (before != null && before.getCapacity() != null && after.getCapacity() != null
                    && !before.getCapacity().equals(after.getCapacity())) {
                int enrolled = enrollmentRepository.countByCourse_IdAndEnrollmentStatus(
                        courseId, EnrollmentStatus.COMPLETED
                );
                int remaining = Math.max(0, after.getCapacity() - enrolled);
                redisTemplate.opsForValue().set("enrollment:course:" + courseId, String.valueOf(remaining));
                log.info("정원 변경 반영 - courseId: {}, 잔여: {}", courseId, remaining);
            }

        } catch (Exception e) {
            log.error("course-events 처리 실패: {}", e.getMessage(), e);
        }
    }
}
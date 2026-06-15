package org.classq.domain.enrollment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.enrollment.entity.Enrollment;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.classq.domain.enrollment.producer.dto.EnrollmentEvent;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.classq.domain.student.entity.Student;
import org.classq.domain.student.repository.StudentRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentConsumer {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;    // 직렬화 or 역직렬화

    @Transactional
    @KafkaListener(
            topics = "enrollment-events",
            groupId = "enrollment-processor",
            containerFactory = "enrollmentListenerContainerFactory"
    )

    /*
    *   {
    "studentId": 1,
    "courseId": 5,
    "credits": 3,
    "schedules": [
      {
        "day": "MON",
        "startTime": "09:00:00",
        "endTime": "11:00:00"
      },
      {
        "day": "WED",
        "startTime": "09:00:00",
        "endTime": "11:00:00"
      }
    ]
  }
    * */

    public void consume(String message) throws Exception {
        EnrollmentEvent event = objectMapper.readValue(message, EnrollmentEvent.class);

        Student student = studentRepository.getReferenceById(event.getStudentId());
        Course course = courseRepository.getReferenceById(event.getCourseId());

        // 취소 후 재수강신청 시 CANCELLED 행이 남아있어 INSERT 시 unique constraint 위반
        // 기존 행이 있으면 COMPLETED로 재활성화, 없으면 새로 INSERT
        // 이미 COMPLETED인 경우(중복 소비)는 Redis 동기화를 건너뜀
        boolean[] shouldSync = {false};
        enrollmentRepository
                .findByStudent_IdAndCourse_IdAndDeletedAtIsNull(event.getStudentId(), event.getCourseId())
                .ifPresentOrElse(
                        enrollment -> {
                            if (enrollment.getEnrollmentStatus() == EnrollmentStatus.CANCELLED) {
                                enrollment.reactivate();
                                shouldSync[0] = true;
                            }
                        },
                        () -> {
                            enrollmentRepository.save(
                                    Enrollment.builder().student(student).course(course).build()
                            );
                            shouldSync[0] = true;
                        }
                );

        if (shouldSync[0]) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    String scheduleKey = "schedule:student:" + event.getStudentId();
                    for (EnrollmentEvent.ScheduleEntry s : event.getSchedules()) {
                        redisTemplate.opsForSet().add(scheduleKey,
                                s.getDay() + "|" + s.getStartTime() + "|" + s.getEndTime());
                    }      // schedule:student:123 -> {"MON|09:00|11:00", "WED|14:00|16:00", ... } set타입으로 저장됨

                    redisTemplate.opsForValue().increment("credits:student:" + event.getStudentId(), event.getCredits());
                }
            });
        }

        log.info("수강신청 처리 완료 - studentId: {}, courseId: {}", event.getStudentId(), event.getCourseId());
    }
}

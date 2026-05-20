package org.classq.domain.enrollment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.classq.domain.enrollment.entity.Enrollment;
import org.classq.domain.enrollment.producer.dto.EnrollmentCancelEvent;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.classq.domain.notification.entity.Notification;
import org.classq.domain.notification.entity.NotificationType;
import org.classq.domain.notification.repository.NotificationRepository;
import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.classq.domain.waitlist.repository.WaitlistRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentCancelConsumer {

    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final NotificationRepository notificationRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(
            topics = "enrollment-cancel-events",
            groupId = "enrollment-processor",
            containerFactory = "enrollmentListenerContainerFactory"
    )
    public void consume(String message) throws Exception {
        EnrollmentCancelEvent event = objectMapper.readValue(message, EnrollmentCancelEvent.class);

        // 1. RDS enrollment 상태 CANCELLED로 변경
        Enrollment enrollment = enrollmentRepository.findById(event.getEnrollmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        enrollment.cancel();

        // 2. 대기자 확인 → 있으면 NOTIFIED 처리 + 알림 저장
        Optional<Waitlist> waitlistOpt = waitlistRepository.findFirstByCourse_IdAndWaitlistStatusOrderByRankAsc(event.getCourseId(), WaitlistStatus.WAITING);

        if (waitlistOpt.isPresent()) {
            Waitlist waitlist = waitlistOpt.get();
            waitlist.notified();

            notificationRepository.save(
                    Notification.builder()
                            .student(waitlist.getStudent())
                            .course(waitlist.getCourse())
                            .notificationType(NotificationType.WAITLIST_AVAILABLE)
                            .message("수강 신청 자리가 생겼습니다. 10분 내에 수락해 주세요.")
                            .build()
            );

            // Redis lock 설정 (새 수강신청 차단)
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { // 커밋 후 실행할 작업
                    // TTL 15분: 수락 시간(10분) + Scheduler 처리 여유 / Scheduler 장애 시 자동 해제 안전망
                    redisTemplate.opsForValue().set("lock:course:" + event.getCourseId(), "1", 15, TimeUnit.MINUTES);
                }
            });
        }

        log.info("수강신청 취소 처리 완료 - enrollmentId: {}, courseId: {}", event.getEnrollmentId(), event.getCourseId());
    }
}

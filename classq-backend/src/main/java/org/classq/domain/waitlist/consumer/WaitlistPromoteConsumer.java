package org.classq.domain.waitlist.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.classq.domain.notification.entity.Notification;
import org.classq.domain.notification.entity.NotificationType;
import org.classq.domain.notification.repository.NotificationRepository;
import org.classq.domain.notification.service.SseEmitterService;
import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.classq.domain.waitlist.producer.dto.WaitlistPromoteEvent;
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
public class WaitlistPromoteConsumer {

    private final WaitlistRepository waitlistRepository;
    private final NotificationRepository notificationRepository;
    private final SseEmitterService sseEmitterService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(
            topics = "waitlist-promote-events",
            groupId = "enrollment-processor",
            containerFactory = "enrollmentListenerContainerFactory"
    )
    public void consume(String message) throws Exception {
        WaitlistPromoteEvent event = objectMapper.readValue(message, WaitlistPromoteEvent.class);
        Long courseId = event.getCourseId();

        // 멱등성 보완 — 이미 NOTIFIED 대기자가 있으면 중복 알림 방지
        boolean alreadyNotified = waitlistRepository
                .findFirstByCourse_IdAndWaitlistStatusAndDeletedAtIsNullOrderByRankAsc(courseId, WaitlistStatus.NOTIFIED)
                .isPresent();
        if (alreadyNotified) {
            log.info("이미 NOTIFIED 대기자 존재 — 스킵 courseId: {}", courseId);
            return;
        }

        Optional<Waitlist> nextOpt = waitlistRepository
                .findFirstByCourse_IdAndWaitlistStatusAndDeletedAtIsNullOrderByRankAsc(courseId, WaitlistStatus.WAITING);

        if (nextOpt.isEmpty()) {
            redisTemplate.delete("lock:course:" + courseId);
            log.info("대기자 없음 — 락 해제 courseId: {}", courseId);
            return;
        }

        Waitlist next = nextOpt.get();
        next.notified();

        Notification notification = notificationRepository.save(
                Notification.builder()
                        .student(next.getStudent())
                        .course(next.getCourse())
                        .notificationType(NotificationType.WAITLIST_AVAILABLE)
                        .message("수강 신청 자리가 생겼습니다. 10분 내에 수락해 주세요.")
                        .build()
        );

        Long studentId = next.getStudent().getId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // TTL 15분: 수락 시간(10분) + Scheduler 처리 여유
                redisTemplate.opsForValue().set("lock:course:" + courseId, "1", 15, TimeUnit.MINUTES);
                sseEmitterService.send(studentId, notification);
            }
        });

        log.info("다음 대기자 알림 발송 — courseId: {}, waitlistId: {}", courseId, next.getId());
    }
}

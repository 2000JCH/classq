package org.classq.domain.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.classq.domain.notification.entity.Notification;
import org.classq.domain.notification.entity.NotificationType;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseEmitterService {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30분
    private static final long HEARTBEAT_INTERVAL = 30 * 1000L; // 30초

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long studentId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> emitters.remove(studentId));
        emitter.onTimeout(() -> emitters.remove(studentId));
        emitter.onError(e -> emitters.remove(studentId));

        emitters.put(studentId, emitter);

        // 연결 직후 heartbeat — SSE 스펙상 첫 이벤트를 보내야 연결이 확립됨
        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            emitters.remove(studentId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void send(Long studentId, Notification notification) {
        SseEmitter emitter = emitters.get(studentId);
        if (emitter == null) {
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "type", notification.getNotificationType().name(),
                    "notificationId", notification.getId(),
                    "message", notification.getMessage()
            );
            emitter.send(SseEmitter.event()
                    .name(resolveEventName(notification.getNotificationType()))
                    .data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("SSE 전송 실패 - studentId: {}", studentId, e);
            emitters.remove(studentId);
            emitter.completeWithError(e);
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL)
    public void sendHeartbeatToAll() {
        emitters.forEach((studentId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data(""));
            } catch (IOException e) {
                log.warn("SSE heartbeat 전송 실패 - studentId: {}", studentId, e);
                emitters.remove(studentId);
                emitter.completeWithError(e);
            }
        });
    }

    private String resolveEventName(NotificationType type) {
        return switch (type) {
            case WAITLIST_AVAILABLE -> "waitlist-available";
            case WAITLIST_EXPIRED -> "waitlist-expired";
            case WAITLIST_CANCELLED -> "waitlist-cancelled";
            case COURSE_CLOSED -> "course-closed";
            case CREDIT_EXCEEDED -> "credit-exceeded";
            case TIME_CONFLICT -> "time-conflict";
        };
    }
}
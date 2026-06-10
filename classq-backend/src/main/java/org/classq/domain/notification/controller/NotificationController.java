package org.classq.domain.notification.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.notification.dto.NotificationResponseDto;
import org.classq.domain.notification.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    // SSE 구독 — 실시간 알림 수신
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Long accountId) {
        return notificationService.subscribe(accountId);
    }

    // 내 알림 목록 조회
    @GetMapping
    public ResponseEntity<List<NotificationResponseDto>> getMyNotifications(
            @AuthenticationPrincipal Long accountId) {
        return ResponseEntity.ok(notificationService.getMyNotifications(accountId));
    }

    // 알림 읽음 처리
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal Long accountId,
            @PathVariable Long notificationId) {
        notificationService.markAsRead(accountId, notificationId);
        return ResponseEntity.ok().build();
    }
}

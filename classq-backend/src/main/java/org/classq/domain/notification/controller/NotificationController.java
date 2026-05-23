package org.classq.domain.notification.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.notification.dto.NotificationResponseDto;
import org.classq.domain.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    // 내 알림 목록 조회
    @GetMapping
    public ResponseEntity<List<NotificationResponseDto>> getMyNotifications(
            @AuthenticationPrincipal Long accountId) {
        return ResponseEntity.ok(notificationService.getMyNotifications(accountId));
    }
}

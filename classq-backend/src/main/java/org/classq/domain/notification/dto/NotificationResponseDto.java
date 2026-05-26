package org.classq.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.classq.domain.notification.entity.NotificationType;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class NotificationResponseDto {

    private Long notificationId;
    private Long courseId;
    private String courseName;
    private NotificationType type;
    private String message;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}

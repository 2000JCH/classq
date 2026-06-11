package org.classq.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.notification.dto.NotificationResponseDto;
import org.classq.domain.notification.entity.Notification;
import org.classq.domain.notification.repository.NotificationRepository;
import org.classq.domain.student.entity.Student;
import org.classq.domain.student.repository.StudentRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final StudentRepository studentRepository;
    private final NotificationRepository notificationRepository;
    private final SseEmitterService sseEmitterService;

    // SSE 구독
    public SseEmitter subscribe(Long accountId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
        return sseEmitterService.subscribe(student.getId());
    }

    // 내 알림 목록 조회
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getMyNotifications(Long accountId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        return notificationRepository.findByStudentIdWithCourse(student.getId())
                .stream()
                .map(n -> new NotificationResponseDto(
                        n.getId(),
                        n.getCourse().getId(),
                        n.getCourse().getName(),
                        n.getNotificationType(),
                        n.getMessage(),
                        n.getReadAt(),
                        n.getCreatedAt()
                ))
                .toList();
    }

    // 알림 읽음 처리
    @Transactional
    public void markAsRead(Long accountId, Long notificationId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Notification notification = notificationRepository.findByIdAndStudentId(notificationId, student.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markAsRead();
    }
}

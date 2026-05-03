package org.classq.domain.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.classq.domain.course.entity.Course;
import org.classq.domain.student.entity.Student;
import org.classq.global.entity.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType notificationType;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}

package org.classq.domain.waitlist.entity;

import jakarta.persistence.*;
import lombok.*;
import org.classq.domain.course.entity.Course;
import org.classq.domain.student.entity.Student;
import org.classq.global.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder
public class Waitlist extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //대기 등록한 학생
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    //대기 걸린 강의
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    //대기 순번
    @Column(nullable = false)
    private int rank;

    //대기 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WaitlistStatus waitlistStatus =  WaitlistStatus.WAITING;

    //수락 만료 시간 10분 지나면 Scheduler -> EXPIRED 처리
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;
}

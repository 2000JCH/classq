package org.classq.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.classq.domain.enrollment.entity.Enrollment;
import org.classq.domain.enrollment.entity.EnrollmentStatus;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminEnrollmentResponseDto {

    private Long enrollmentId;
    private Long studentId;
    private String studentName;
    private EnrollmentStatus status;
    private LocalDateTime createdAt;

    public static AdminEnrollmentResponseDto from(Enrollment enrollment) {
        return new AdminEnrollmentResponseDto(
                enrollment.getId(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().getName(),
                enrollment.getEnrollmentStatus(),
                enrollment.getCreatedAt()
        );
    }
}

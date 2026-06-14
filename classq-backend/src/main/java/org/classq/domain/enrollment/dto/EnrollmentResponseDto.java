package org.classq.domain.enrollment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.classq.domain.course.entity.enums.CourseType;
import org.classq.domain.enrollment.entity.EnrollmentStatus;

@Getter
@AllArgsConstructor
public class EnrollmentResponseDto {

    private Long enrollmentId;
    private Long courseId;
    private String courseName;
    private int credits;
    private String professorName;
    private EnrollmentStatus status;
    private CourseType courseType;
}
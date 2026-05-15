package org.classq.domain.course.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.classq.domain.course.entity.enums.ClassMode;

@Getter
@NoArgsConstructor
public class CourseUpdateRequestDto {

    private String name;
    private ClassMode classMode;
    private Long departmentId;
    private int capacity;
    private int waitlistLimit;
    private Integer minGrade;
    private Integer maxGrade;
}

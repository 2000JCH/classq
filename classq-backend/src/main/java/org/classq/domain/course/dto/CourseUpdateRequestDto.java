package org.classq.domain.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.classq.domain.course.entity.enums.ClassMode;

@Getter
@NoArgsConstructor
public class CourseUpdateRequestDto {

    @NotBlank
    private String name;

    @NotNull
    private ClassMode classMode;

    private Long departmentId;

    @NotNull
    @Min(1)
    private Integer capacity;

    @NotNull
    @Min(0)
    private Integer waitlistLimit;

    @Min(1)
    private Integer minGrade;

    @Min(1)
    private Integer maxGrade;
}

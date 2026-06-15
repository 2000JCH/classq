package org.classq.domain.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EnrollmentRequestDto {

    @NotNull
    private Long courseId;
}
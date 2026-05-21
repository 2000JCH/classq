package org.classq.domain.waitlist.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WaitlistRequestDto {

    @NotNull
    private Long courseId;
}

package org.classq.domain.waitlist.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.classq.domain.waitlist.entity.WaitlistStatus;

@Getter
@AllArgsConstructor
public class WaitlistResponseDto {

    private Long waitlistId;
    private Long courseId;
    private String courseName;
    private int rank;
    private WaitlistStatus status;
}

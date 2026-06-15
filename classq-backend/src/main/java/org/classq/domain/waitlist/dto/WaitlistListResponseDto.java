package org.classq.domain.waitlist.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class WaitlistListResponseDto {

    private List<WaitlistResponseDto> waitlists;
    private int currentCredits;
    private int maxCredits;
}

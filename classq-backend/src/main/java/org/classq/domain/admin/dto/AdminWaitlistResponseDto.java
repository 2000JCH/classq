package org.classq.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminWaitlistResponseDto {

    private Long waitlistId;
    private Long studentId;
    private String studentName;
    private int rank;
    private WaitlistStatus status;
    private LocalDateTime expiredAt;

    public static AdminWaitlistResponseDto from(Waitlist waitlist) {
        return new AdminWaitlistResponseDto(
                waitlist.getId(),
                waitlist.getStudent().getId(),
                waitlist.getStudent().getName(),
                waitlist.getRank(),
                waitlist.getWaitlistStatus(),
                waitlist.getExpiredAt()
        );
    }
}

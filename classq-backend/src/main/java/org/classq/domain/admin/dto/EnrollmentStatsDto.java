package org.classq.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EnrollmentStatsDto {

    private long todayCount;
    private long totalCount;
    private long cancelledCount;
}
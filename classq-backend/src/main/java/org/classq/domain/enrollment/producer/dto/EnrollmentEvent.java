package org.classq.domain.enrollment.producer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentEvent {

    private Long studentId;
    private Long courseId;
    private int credits;
    private List<ScheduleEntry> schedules;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleEntry {
        private String day;
        private LocalTime startTime;
        private LocalTime endTime;
    }
}
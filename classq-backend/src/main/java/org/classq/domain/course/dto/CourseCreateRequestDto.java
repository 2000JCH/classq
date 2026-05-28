package org.classq.domain.course.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.classq.domain.course.entity.enums.ClassMode;
import org.classq.domain.course.entity.enums.ClassType;
import org.classq.domain.course.entity.enums.CourseScheduleDay;
import org.classq.domain.course.entity.enums.CourseType;

import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class CourseCreateRequestDto {

    private String name;
    private CourseType courseType;
    private ClassType classType;
    private ClassMode classMode;
    private int credits;
    private int capacity;   // 수강 정원
    private int waitlistLimit;
    private Long departmentId;
    private Integer minGrade;
    private Integer maxGrade;
    private List<ScheduleRequest> schedules;

    @Getter
    @NoArgsConstructor
    public static class ScheduleRequest {
        private CourseScheduleDay day;
        private LocalTime startTime;
        private LocalTime endTime;
    }
}
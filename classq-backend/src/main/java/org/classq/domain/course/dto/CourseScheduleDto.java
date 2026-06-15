package org.classq.domain.course.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.classq.domain.course.entity.enums.CourseScheduleDay;

import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class CourseScheduleDto {

    private Long id;
    private CourseScheduleDay day;
    private LocalTime startTime;
    private LocalTime endTime;
}

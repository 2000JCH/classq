package org.classq.domain.course.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseSnapshotDto {

    private Long id;
    private Integer capacity;   // 강의 정원(최대 수강 인원)
    private String status;
}
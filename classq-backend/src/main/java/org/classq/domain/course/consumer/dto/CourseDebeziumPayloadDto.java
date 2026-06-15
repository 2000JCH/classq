package org.classq.domain.course.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseDebeziumPayloadDto {

    private CourseSnapshotDto before;
    private CourseSnapshotDto after;
    private String op;  // 어떤 작업인지 (u=update, c=insert, d=delete)
}
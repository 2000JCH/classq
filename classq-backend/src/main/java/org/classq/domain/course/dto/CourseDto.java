package org.classq.domain.course.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.classq.domain.course.entity.enums.ClassMode;
import org.classq.domain.course.entity.enums.ClassType;
import org.classq.domain.course.entity.enums.CourseStatus;
import org.classq.domain.course.entity.enums.CourseType;

@Getter
@AllArgsConstructor
public class CourseDto {

    private Long id;
    private String name;    //강의명
    private String professorName;
    private String departmentName;
    private CourseType courseType;  // 전필/전선/교양
    private ClassType classType;    // 이론/실습
    private ClassMode classMode;    // 온라인/오프라인
    private int credit; // 학점
    private int capacity;   // 수강 정원
    private Integer minGrade;   // 수강 가능 최소 학년
    private Integer maxGrade;   // 수강 가능 최대 학년
    private CourseStatus courseStatus;  // ACTIVE / CLOSED

}

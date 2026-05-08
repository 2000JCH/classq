package org.classq.domain.student.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor

public class StudentResponseDto {
    private String email;
    private String name;
    private int grade;
    private String departmentName;
}

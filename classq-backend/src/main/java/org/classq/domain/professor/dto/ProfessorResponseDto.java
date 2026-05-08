package org.classq.domain.professor.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProfessorResponseDto {
    private String name;
    private String email;
    private String departmentName;
}

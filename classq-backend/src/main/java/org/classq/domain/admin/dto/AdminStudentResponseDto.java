package org.classq.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.classq.domain.student.entity.Student;

@Getter
@AllArgsConstructor
public class AdminStudentResponseDto {

    private Long id;
    private String email;
    private String name;
    private int grade;
    private String departmentName;

    public static AdminStudentResponseDto from(Student student) {
        return new AdminStudentResponseDto(
                student.getId(),
                student.getAccount().getEmail(),
                student.getName(),
                student.getGrade(),
                student.getDepartment().getName()
        );
    }
}
package org.classq.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.classq.domain.professor.entity.Professor;

@Getter
@AllArgsConstructor
public class PendingProfessorDto {
    private Long accountId;
    private String email;
    private String name;
    private String departmentName;

    public static PendingProfessorDto from(Professor professor) {
        return new PendingProfessorDto(
                professor.getAccount().getId(),
                professor.getAccount().getEmail(),
                professor.getName(),
                professor.getDepartment().getName()
        );
    }
}

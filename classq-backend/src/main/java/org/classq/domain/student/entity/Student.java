package org.classq.domain.student.entity;

import jakarta.persistence.*;
import lombok.*;
import org.classq.domain.account.entity.Account;
import org.classq.domain.department.entity.Department;
import org.classq.global.entity.BaseEntity;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder
public class Student extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int grade;   // 학년

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    // 내(학생) 정보 수정
    public void update(String name, Integer grade) {
        this.name = name;
        this.grade = grade;
    }
}

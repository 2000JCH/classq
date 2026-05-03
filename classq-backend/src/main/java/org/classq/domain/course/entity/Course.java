package org.classq.domain.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.classq.domain.course.entity.enums.ClassMode;
import org.classq.domain.course.entity.enums.ClassType;
import org.classq.domain.course.entity.enums.CourseStatus;
import org.classq.domain.course.entity.enums.CourseType;
import org.classq.domain.department.entity.Department;
import org.classq.domain.professor.entity.Professor;
import org.classq.global.entity.BaseEntity;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
@Getter
@Builder
public class Course extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id", nullable = false)
    private Professor professor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    // 강의명
    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_type", nullable = false)
    private CourseType courseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_type", nullable = false)
    private ClassType classType;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_mode", nullable = false)
    private ClassMode classMode;

    //학점
    @Column(nullable = false)
    private int credits;

    //수강 정원
    @Column(nullable = false)
    private int capacity;

    @Column(name = "waitlist_limit", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int waitlistLimit;

    @Column(name = "min_grade")
    private Integer minGrade;

    @Column(name = "max_grade")
    private Integer maxGrade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CourseStatus courseStatus = CourseStatus.ACTIVE;
}

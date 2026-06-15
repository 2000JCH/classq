package org.classq.domain.enrollment.repository;

import org.classq.domain.enrollment.dto.EnrollmentResponseDto;
import org.classq.domain.enrollment.entity.Enrollment;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    int countByCourse_IdAndEnrollmentStatusAndDeletedAtIsNull(Long courseId, EnrollmentStatus status);

    // 이미 수강신청 완료했는지 확인
    boolean existsByStudent_IdAndCourse_IdAndEnrollmentStatusAndDeletedAtIsNull(
            Long studentId, Long courseId, EnrollmentStatus status);

    // 재수강신청 시 기존 CANCELLED 행 조회
    Optional<Enrollment> findByStudent_IdAndCourse_IdAndDeletedAtIsNull(Long studentId, Long courseId);

    Optional<Enrollment> findByIdAndStudent_IdAndEnrollmentStatusAndDeletedAtIsNull(
            Long id, Long studentId, EnrollmentStatus status);

    @Query("SELECT COALESCE(SUM(e.course.credits), 0) FROM Enrollment e " +
            "WHERE e.student.id = :studentId AND e.enrollmentStatus = 'COMPLETED' AND e.deletedAt IS NULL")
    long sumCreditsByStudentId(@Param("studentId") Long studentId);

    // 특정 강의의 soft delete 되지 않은 수강신청 목록 페이징 조회
    Page<Enrollment> findByCourse_IdAndDeletedAtIsNull(Long courseId, Pageable pageable);

    // 폐강 시 해당 강의 수강 중인 학생 전체 조회
    List<Enrollment> findByCourse_IdAndEnrollmentStatusAndDeletedAtIsNull(Long courseId, EnrollmentStatus status);

    // {------------ 수강신청 현황 통계 -----------------}
    // 전체 수강신청 수 (soft delete 제외)
    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.deletedAt IS NULL")
    long countTotal();

    // 취소된 수강신청 수
    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.enrollmentStatus = 'CANCELLED' AND e.deletedAt IS NULL")
    long countCancelled();

    // 오늘 수강신청 수 (COMPLETED)
    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.enrollmentStatus = 'COMPLETED' AND e.createdAt >= :startOfDay AND e.deletedAt IS NULL")
    long countToday(@Param("startOfDay") LocalDateTime startOfDay);
    // -------------------------------------------

    @Query("SELECT new org.classq.domain.enrollment.dto.EnrollmentResponseDto(" +
            "e.id, e.course.id, e.course.name, e.course.credits, e.course.professor.name, e.enrollmentStatus, e.course.courseType) " +
            "FROM Enrollment e " +
            "WHERE e.student.id = :studentId AND e.deletedAt IS NULL")
    List<EnrollmentResponseDto> findMyEnrollments(@Param("studentId") Long studentId);
}
package org.classq.domain.waitlist.repository;

import jakarta.persistence.LockModeType;
import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    // 대기자 취소 동시성 보완 — 같은 waitlistId에 중복 취소 요청이 들어올 때 두 번째 요청 차단
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Waitlist w WHERE w.id = :id AND w.deletedAt IS NULL")
    Optional<Waitlist> findByIdForUpdate(@Param("id") Long id);

    // rank 1번 WAITING 대기자 조회 (Cancel Consumer에서 사용)
    Optional<Waitlist> findFirstByCourse_IdAndWaitlistStatusAndDeletedAtIsNullOrderByRankAsc(Long courseId, WaitlistStatus status);

    // rank 1번 WAITING 대기자 조회 + 비관적 락 (Cancel Consumer TOCTOU 방지)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Waitlist w WHERE w.course.id = :courseId AND w.waitlistStatus = :status AND w.deletedAt IS NULL ORDER BY w.rank ASC")
    List<Waitlist> findTopWaitingByCourseIdForUpdate(@Param("courseId") Long courseId, @Param("status") WaitlistStatus status, Pageable pageable);

    // 이미 대기 신청 여부 확인
    boolean existsByCourse_IdAndStudent_IdAndWaitlistStatusInAndDeletedAtIsNull(Long courseId, Long studentId, List<WaitlistStatus> statuses);

    // 다음 rank 계산용 (현재 활성 대기자 수)
    int countByCourse_IdAndWaitlistStatusInAndDeletedAtIsNull(Long courseId, List<WaitlistStatus> statuses);

    // 내 대기 목록 조회 (WAITING, NOTIFIED만)
    List<Waitlist> findByStudent_IdAndWaitlistStatusInAndDeletedAtIsNull(Long studentId, List<WaitlistStatus> statuses);

    // 만료 시간 초과된 NOTIFIED 대기자 목록 (Scheduler용)
    List<Waitlist> findByWaitlistStatusAndExpiredAtBeforeAndDeletedAtIsNull(WaitlistStatus status, LocalDateTime now);

    // 특정 강의의 soft delete 되지 않은 대기자 목록 페이징 조회 (rank 오름차순)
    Page<Waitlist> findByCourse_IdAndDeletedAtIsNullOrderByRankAsc(Long courseId, Pageable pageable);

    // 취소 후 재등록 시 soft-delete된 기존 이력 조회 (upsert용)
    Optional<Waitlist> findByStudent_IdAndCourse_IdAndDeletedAtIsNotNull(Long studentId, Long courseId);

    // 폐강 시 해당 강의 활성 대기자 전체 조회
    List<Waitlist> findByCourse_IdAndWaitlistStatusInAndDeletedAtIsNull(Long courseId, List<WaitlistStatus> statuses);

    // 특정 rank보다 뒤에 있는 활성 대기자 rank 일괄 감소 (대기자 이탈 시 순번 갱신)
    @Modifying
    @Query("UPDATE Waitlist w SET w.rank = w.rank - 1 WHERE w.course.id = :courseId AND w.rank > :rank AND w.deletedAt IS NULL AND w.waitlistStatus IN ('WAITING', 'NOTIFIED')")
    void decrementRanksAfter(@Param("courseId") Long courseId, @Param("rank") int rank);
}

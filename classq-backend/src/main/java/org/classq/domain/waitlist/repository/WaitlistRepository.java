package org.classq.domain.waitlist.repository;

import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    // rank 1번 WAITING 대기자 조회 (Cancel Consumer에서 사용)
    Optional<Waitlist> findFirstByCourse_IdAndWaitlistStatusAndDeletedAtIsNullOrderByRankAsc(Long courseId, WaitlistStatus status);

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
}

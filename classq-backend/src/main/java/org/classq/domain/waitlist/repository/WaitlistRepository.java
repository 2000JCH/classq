package org.classq.domain.waitlist.repository;

import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    /**
     *   SELECT * FROM waitlist
     *   WHERE course_id = 5
     *   AND status = 'WAITING'
     *   ORDER BY rank ASC
     *   LIMIT 1
     *
     *   대기자 여부 조회
     *   대기자가 있으면 rank 1번 대기자 정보까지 가져옴 (Waitlist 객체째로 가져온다)
     * */
    // rank 1번 WAITING 대기자 조회 (Cancel Consumer에서 사용)
    Optional<Waitlist> findFirstByCourse_IdAndWaitlistStatusOrderByRankAsc(Long courseId, WaitlistStatus status);

    // 이미 대기 신청 여부 확인
    boolean existsByCourse_IdAndStudent_IdAndWaitlistStatusInAndDeletedAtIsNull(Long courseId, Long studentId, List<WaitlistStatus> statuses);

    // 다음 rank 계산용 (현재 활성 대기자 수)
    int countByCourse_IdAndWaitlistStatusInAndDeletedAtIsNull(Long courseId, List<WaitlistStatus> statuses);
}

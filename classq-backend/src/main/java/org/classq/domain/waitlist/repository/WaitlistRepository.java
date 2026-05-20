package org.classq.domain.waitlist.repository;

import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
    Optional<Waitlist> findFirstByCourse_IdAndWaitlistStatusOrderByRankAsc(Long courseId, WaitlistStatus status);
}

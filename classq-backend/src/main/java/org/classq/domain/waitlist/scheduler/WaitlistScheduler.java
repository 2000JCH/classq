package org.classq.domain.waitlist.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.classq.domain.waitlist.repository.WaitlistRepository;
import org.classq.domain.waitlist.service.WaitlistService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistScheduler {

    private final WaitlistRepository waitlistRepository;
    private final WaitlistService waitlistService;

    @Scheduled(fixedDelay = 60000)
    public void processExpiredWaitlists() {
        List<Waitlist> expired = waitlistRepository
                .findByWaitlistStatusAndExpiredAtBeforeAndDeletedAtIsNull(WaitlistStatus.NOTIFIED, LocalDateTime.now());

        for (Waitlist waitlist : expired) {
            try {
                waitlistService.expireAndPromoteNext(waitlist);
                log.info("대기 만료 처리 - waitlistId: {}, courseId: {}", waitlist.getId(), waitlist.getCourse().getId());
            } catch (Exception e) {
                log.error("대기 만료 처리 실패 - waitlistId: {}", waitlist.getId(), e);
            }
        }
    }
}

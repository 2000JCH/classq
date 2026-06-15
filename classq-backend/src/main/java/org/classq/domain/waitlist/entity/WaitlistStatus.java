package org.classq.domain.waitlist.entity;

public enum WaitlistStatus {
    WAITING,    // 대기 중
    NOTIFIED,   // 수락 대기 중 (자리 났다고 알림 받은 상태)
    EXPIRED,    // 만료 (10분 안에 응답 안 했음)
    COMPLETED,  // 수락 완료
}

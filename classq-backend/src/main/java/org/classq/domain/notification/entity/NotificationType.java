package org.classq.domain.notification.entity;

public enum NotificationType {
    WAITLIST_AVAILABLE, //대기 순번이 돌아와서 수락 가능
    WAITLIST_EXPIRED,   //10분 내 미수락으로 대기 만료
    WAITLIST_CANCELLED, //대기자가 직접 대기 취소
    COURSE_CLOSED,  //수강/대기 중인 강의가 폐강됨
    CREDIT_EXCEEDED,    //수락 시 학점 초과로 자동 다음 순번으로 넘어감
    TIME_CONFLICT   //수락 시 시간표 중복으로 자동 다음 순번으로 넘어감
}

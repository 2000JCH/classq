package org.classq.global.kafka.event;

public record EnrollmentCancelEvent(
        Long enrollmentId,
        Long courseId,
        Long studentId
) {}
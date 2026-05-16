package org.classq.global.kafka.event;

public record EnrollmentEvent(
        Long courseId,
        Long studentId
) {}
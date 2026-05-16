package org.classq.global.kafka;

import lombok.RequiredArgsConstructor;
import org.classq.global.kafka.event.EnrollmentCancelEvent;
import org.classq.global.kafka.event.EnrollmentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEnrollmentEvent(EnrollmentEvent event) {
        kafkaTemplate.send("enrollment-events", String.valueOf(event.courseId()), event);
    }

    public void publishEnrollmentCancelEvent(EnrollmentCancelEvent event) {
        kafkaTemplate.send("enrollment-cancel-events", String.valueOf(event.courseId()), event);
    }
}
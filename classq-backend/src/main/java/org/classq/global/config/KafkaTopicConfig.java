package org.classq.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic enrollmentEventsTopic() {
        return TopicBuilder.name("enrollment-events")
                .partitions(3)
                .build();
    }

    @Bean
    public NewTopic enrollmentCancelEventsTopic() {
        return TopicBuilder.name("enrollment-cancel-events")
                .partitions(1)
                .build();
    }

    @Bean
    public NewTopic courseEventsTopic() {
        return TopicBuilder.name("course-events")       // 폐강 시
                .partitions(1)
                .build();
    }

    @Bean
    public NewTopic enrollmentDeadLetterTopic() {
        return TopicBuilder.name("enrollment-dead-letter")
                .partitions(1)
                .build();
    }
}

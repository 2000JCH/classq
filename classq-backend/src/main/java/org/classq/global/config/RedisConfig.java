package org.classq.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);   // application.yml에 있는 host/port로 Redis에 접속
        template.setKeySerializer(new StringRedisSerializer()); // Redis 키를 String으로 저장
        template.setValueSerializer(new StringRedisSerializer());   //  Redis value를 String으로 저장
        return template;
    }
}

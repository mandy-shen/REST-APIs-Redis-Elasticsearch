package com.monhong.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * avoid spring cache to redis
 * https://stackoverflow.com/questions/39324717/spring-boot-caching-with-redis-key-have-xac-xed-x00-x05t-x00-x06
 *
 * update: just using StringRedisTemplate.
 */
@Configuration
class RedisConfig {

    @Bean
    public StringRedisTemplate redisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }

}

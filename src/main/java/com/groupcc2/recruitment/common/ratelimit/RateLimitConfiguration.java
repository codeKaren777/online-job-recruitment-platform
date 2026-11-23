package com.groupcc2.recruitment.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {
    @Bean
    RateLimitRepository rateLimitRepository(JdbcTemplate jdbcTemplate) {
        return new RateLimitRepository(jdbcTemplate);
    }

    @Bean
    RateLimitFilter rateLimitFilter(RateLimitProperties properties,
                                    RateLimitRepository repository,
                                    ObjectMapper objectMapper) {
        return new RateLimitFilter(properties, repository, objectMapper);
    }
}

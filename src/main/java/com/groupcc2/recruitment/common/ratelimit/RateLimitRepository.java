package com.groupcc2.recruitment.common.ratelimit;

import org.springframework.jdbc.core.JdbcTemplate;
public class RateLimitRepository {
    private final JdbcTemplate jdbcTemplate;

    public RateLimitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int incrementAndGet(String bucketKey, int windowSeconds) {
        return jdbcTemplate.queryForObject("""
                SELECT recruitment.increment_rate_limit(?, ?)
                """, Integer.class, bucketKey, windowSeconds);
    }
}

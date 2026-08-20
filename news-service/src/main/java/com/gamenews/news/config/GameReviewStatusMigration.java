package com.gamenews.news.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GameReviewStatusMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int migrated = jdbcTemplate.update(
                "UPDATE games SET review_status = 'CONFIRMED' WHERE review_status = 'AI_CREATED'");

        if (migrated > 0) {
            log.info("[GameReviewStatusMigration] AI_CREATED -> CONFIRMED migrated={}", migrated);
        }
    }
}

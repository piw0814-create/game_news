package com.gamenews.news.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TopicFranchiseBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO topic_franchises (
                    topic_id,
                    franchise_id,
                    is_primary,
                    relevance_score,
                    created_at
                )
                SELECT
                    ta.topic_id,
                    af.franchise_id,
                    MAX(af.is_primary),
                    MAX(af.confidence_score),
                    UTC_TIMESTAMP(6)
                FROM topic_articles ta
                JOIN article_franchises af ON af.article_id = ta.article_id
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM topic_franchises tf
                    WHERE tf.topic_id = ta.topic_id
                      AND tf.franchise_id = af.franchise_id
                )
                GROUP BY ta.topic_id, af.franchise_id
                """);

        if (inserted > 0) {
            log.info("[TopicFranchiseBackfill] 기존 ArticleFranchise -> TopicFranchise backfill inserted={}", inserted);
        }
    }
}

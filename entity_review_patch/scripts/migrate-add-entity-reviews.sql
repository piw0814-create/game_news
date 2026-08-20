CREATE TABLE IF NOT EXISTS entity_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    entity_kind VARCHAR(20) NOT NULL,
    detected_name VARCHAR(255) NOT NULL,
    ai_entity_type VARCHAR(40) NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    confidence_score DECIMAL(5,4) NULL,
    reason VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    candidate_json LONGTEXT NULL,
    resolved_game_id BIGINT NULL,
    resolved_franchise_id BIGINT NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_entity_reviews_status_created (status, created_at),
    KEY idx_entity_reviews_article (article_id),
    CONSTRAINT fk_entity_reviews_article
        FOREIGN KEY (article_id) REFERENCES news_articles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

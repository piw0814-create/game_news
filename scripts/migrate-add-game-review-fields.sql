-- Existing game_news_db migration: prepare Game records for AI registration and admin review.
-- Existing games remain manually registered and confirmed.

ALTER TABLE games
    ADD COLUMN IF NOT EXISTS registration_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        COMMENT 'MANUAL | AI' AFTER image_url,
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED'
        COMMENT 'CONFIRMED | AI_CREATED | REVIEW_REQUIRED' AFTER registration_source,
    ADD COLUMN IF NOT EXISTS registration_confidence DECIMAL(5,4) NULL AFTER review_status,
    ADD COLUMN IF NOT EXISTS source_article_id BIGINT NULL AFTER registration_confidence;

UPDATE games
SET registration_source = 'MANUAL'
WHERE registration_source IS NULL OR TRIM(registration_source) = '';

UPDATE games
SET review_status = 'CONFIRMED'
WHERE review_status IS NULL OR TRIM(review_status) = '';

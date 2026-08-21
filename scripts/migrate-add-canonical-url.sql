-- URL canonicalization support.
-- Existing rows stay NULL until the ADMIN dry-run/apply backfill API is executed.
ALTER TABLE news_articles
    ADD COLUMN IF NOT EXISTS canonical_url VARCHAR(768) NULL AFTER url;

CREATE UNIQUE INDEX IF NOT EXISTS uq_news_articles_canonical_url
    ON news_articles (canonical_url);

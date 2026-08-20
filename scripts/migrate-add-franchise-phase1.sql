-- Franchise Phase 1 migration (MariaDB)
-- 현재 개발 환경은 JPA ddl-auto=update라 재빌드 시 자동 생성되지만,
-- 운영/수동 마이그레이션을 위해 동일 구조를 명시한다.

CREATE TABLE IF NOT EXISTS franchises (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    igdb_id BIGINT UNIQUE,
    metadata_source VARCHAR(30),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS franchise_aliases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    franchise_id BIGINT NOT NULL,
    alias VARCHAR(255) NOT NULL UNIQUE,
    PRIMARY KEY (id),
    KEY idx_franchise_alias_franchise_id (franchise_id),
    CONSTRAINT fk_franchise_alias_franchise FOREIGN KEY (franchise_id) REFERENCES franchises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS game_franchises (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_id BIGINT NOT NULL,
    franchise_id BIGINT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_game_franchise (game_id, franchise_id),
    CONSTRAINT fk_game_franchise_game FOREIGN KEY (game_id) REFERENCES games(id),
    CONSTRAINT fk_game_franchise_franchise FOREIGN KEY (franchise_id) REFERENCES franchises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS article_franchises (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    franchise_id BIGINT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    confidence_score DECIMAL(5,4),
    relevance_reason TEXT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_article_franchise (article_id, franchise_id),
    CONSTRAINT fk_article_franchise_article FOREIGN KEY (article_id) REFERENCES news_articles(id),
    CONSTRAINT fk_article_franchise_franchise FOREIGN KEY (franchise_id) REFERENCES franchises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

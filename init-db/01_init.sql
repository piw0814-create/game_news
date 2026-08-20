-- 게임 뉴스 AI 인텔리전스 피드 초기 DDL
-- Spring JPA ddl-auto: update 로도 생성되지만
-- 명시적 DDL로 핵심 테이블과 관계를 문서화한다.

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    role        VARCHAR(20)     NOT NULL DEFAULT 'USER' COMMENT 'USER | ADMIN',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 게임 뉴스 서비스의 게임 기준 정보
CREATE TABLE IF NOT EXISTS games (
    id           BIGINT          NOT NULL AUTO_INCREMENT,
    name         VARCHAR(255)    NOT NULL,
    display_name VARCHAR(255),
    publisher               VARCHAR(255),
    developer               VARCHAR(255),
    genre                   VARCHAR(100),
    platform                VARCHAR(255),
    image_url               VARCHAR(1000),
    igdb_id                 BIGINT UNIQUE,
    igdb_game_type          VARCHAR(100),
    version_parent_igdb_id  BIGINT,
    metadata_source         VARCHAR(30),
    enrichment_status       VARCHAR(30) DEFAULT 'NOT_ENRICHED',
    last_enriched_at        DATETIME(6),
    registration_source     VARCHAR(20)     NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL | AI | IGDB',
    created_at              DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 게임 기준명 외에 검색/AI 매칭에 사용할 별칭
CREATE TABLE IF NOT EXISTS game_aliases (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    game_id  BIGINT       NOT NULL,
    alias    VARCHAR(255) NOT NULL UNIQUE,
    PRIMARY KEY (id),
    KEY idx_game_alias_game_id (game_id),
    FOREIGN KEY (game_id) REFERENCES games(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 게임 IP/프랜차이즈 기준 정보 (IGDB Franchise와 연결 가능)
CREATE TABLE IF NOT EXISTS franchises (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(255),
    igdb_id         BIGINT UNIQUE,
    metadata_source VARCHAR(30) COMMENT 'MANUAL | IGDB',
    last_synced_at  DATETIME(6),
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS franchise_aliases (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    franchise_id  BIGINT       NOT NULL,
    alias         VARCHAR(255) NOT NULL UNIQUE,
    PRIMARY KEY (id),
    KEY idx_franchise_alias_franchise_id (franchise_id),
    FOREIGN KEY (franchise_id) REFERENCES franchises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Game은 main franchise 1개 + 기타 franchise 여러 개에 속할 수 있으므로 N:M으로 관리
CREATE TABLE IF NOT EXISTS game_franchises (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    game_id       BIGINT      NOT NULL,
    franchise_id  BIGINT      NOT NULL,
    is_primary    BOOLEAN     NOT NULL DEFAULT FALSE,
    relation_source VARCHAR(20) COMMENT 'MANUAL | IGDB',
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_game_franchise (game_id, franchise_id),
    FOREIGN KEY (game_id) REFERENCES games(id),
    FOREIGN KEY (franchise_id) REFERENCES franchises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 외부에서 수집한 원본 게임 뉴스 기사
CREATE TABLE IF NOT EXISTS news_articles (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    title            VARCHAR(500)    NOT NULL,
    url              VARCHAR(768)    NOT NULL UNIQUE,
    source_name      VARCHAR(255)    NOT NULL,
    source_type      VARCHAR(20)     NOT NULL COMMENT 'OFFICIAL | MEDIA | COMMUNITY',
    published_at     DATETIME(6),
    collected_at     DATETIME(6)     NOT NULL,
    content          LONGTEXT,
    summary          LONGTEXT,
    keywords         LONGTEXT,
    category         VARCHAR(30)     COMMENT 'RELEASE | UPDATE | INDUSTRY | ESPORTS | EVENT | CONTROVERSY | OTHER',
    game_news_relevant BOOLEAN,
    entity_type      VARCHAR(30)     COMMENT 'SPECIFIC_GAME | FRANCHISE | UNNAMED_ENTRY | MIXED | NONE',
    analysis_status  VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | PROCESSING | COMPLETED | FAILED',
    created_at       DATETIME(6)     NOT NULL,
    updated_at       DATETIME(6)     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 여러 기사가 가리키는 하나의 실제 사건
CREATE TABLE IF NOT EXISTS topics (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    title             VARCHAR(500)    NOT NULL,
    summary           LONGTEXT,
    why_important     LONGTEXT,
    category          VARCHAR(30)     COMMENT 'RELEASE | UPDATE | INDUSTRY | ESPORTS | EVENT | CONTROVERSY | OTHER',
    importance_score  INT,
    first_seen_at     DATETIME(6)     NOT NULL,
    last_updated_at   DATETIME(6)     NOT NULL,
    created_at        DATETIME(6)     NOT NULL,
    updated_at        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기사와 게임의 N:M 관계
CREATE TABLE IF NOT EXISTS article_games (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    article_id        BIGINT          NOT NULL,
    game_id           BIGINT          NOT NULL,
    is_primary        BOOLEAN         NOT NULL DEFAULT FALSE,
    confidence_score  DECIMAL(5,4),
    relevance_reason  TEXT,
    created_at        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_article_game (article_id, game_id),
    FOREIGN KEY (article_id) REFERENCES news_articles(id),
    FOREIGN KEY (game_id) REFERENCES games(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기사와 프랜차이즈의 N:M 관계: 특정 작품이 아니라 IP/프랜차이즈 전체를 직접 다룰 때 사용
CREATE TABLE IF NOT EXISTS article_franchises (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    article_id        BIGINT          NOT NULL,
    franchise_id      BIGINT          NOT NULL,
    is_primary        BOOLEAN         NOT NULL DEFAULT FALSE,
    confidence_score  DECIMAL(5,4),
    relevance_reason  TEXT,
    created_at        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_article_franchise (article_id, franchise_id),
    FOREIGN KEY (article_id) REFERENCES news_articles(id),
    FOREIGN KEY (franchise_id) REFERENCES franchises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- AI가 식별을 확정하지 못한 Game / Franchise 후보의 관리자 검토 및 해결 이력
CREATE TABLE IF NOT EXISTS entity_reviews (
    id                    BIGINT          NOT NULL AUTO_INCREMENT,
    article_id            BIGINT          NOT NULL,
    entity_kind           VARCHAR(20)     NOT NULL COMMENT 'GAME | FRANCHISE',
    detected_name         VARCHAR(255)    NOT NULL,
    ai_entity_type        VARCHAR(40),
    is_primary            BOOLEAN         NOT NULL DEFAULT FALSE,
    confidence_score      DECIMAL(5,4),
    reason                VARCHAR(1000),
    status                VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | RESOLVED | REJECTED',
    candidate_json        LONGTEXT,
    resolved_game_id      BIGINT,
    resolved_franchise_id BIGINT,
    resolved_at           DATETIME(6),
    created_at            DATETIME(6)     NOT NULL,
    updated_at            DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_entity_reviews_status_created (status, created_at),
    KEY idx_entity_reviews_article (article_id),
    FOREIGN KEY (article_id) REFERENCES news_articles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- resolved_game_id / resolved_franchise_id는 해결 이력 스냅샷 ID다.
-- 병합 시 애플리케이션이 새 Catalog ID로 재지정하며, 검토 이력 보존을 위해 물리 FK는 두지 않는다.

-- Topic과 기사의 관계. 기사 하나는 정확히 하나의 Topic에만 귀속된다.
CREATE TABLE IF NOT EXISTS topic_articles (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    topic_id    BIGINT      NOT NULL,
    article_id  BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_topic_article_article (article_id),
    FOREIGN KEY (topic_id) REFERENCES topics(id),
    FOREIGN KEY (article_id) REFERENCES news_articles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Topic과 게임의 N:M 관계
CREATE TABLE IF NOT EXISTS topic_games (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    topic_id         BIGINT          NOT NULL,
    game_id          BIGINT          NOT NULL,
    is_primary       BOOLEAN         NOT NULL DEFAULT FALSE,
    relevance_score  DECIMAL(5,4),
    created_at       DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_topic_game (topic_id, game_id),
    FOREIGN KEY (topic_id) REFERENCES topics(id),
    FOREIGN KEY (game_id) REFERENCES games(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- Topic과 프랜차이즈의 N:M 관계: 프랜차이즈 전체 사건 Topic에만 연결
CREATE TABLE IF NOT EXISTS topic_franchises (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    topic_id         BIGINT          NOT NULL,
    franchise_id     BIGINT          NOT NULL,
    is_primary       BOOLEAN         NOT NULL DEFAULT FALSE,
    relevance_score  DECIMAL(5,4),
    created_at       DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_topic_franchise (topic_id, franchise_id),
    FOREIGN KEY (topic_id) REFERENCES topics(id),
    FOREIGN KEY (franchise_id) REFERENCES franchises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- Topic 좋아요 (User Service 경계의 user_id에는 DB FK를 걸지 않음)
CREATE TABLE IF NOT EXISTS topic_likes (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    topic_id    BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_topic_like_user (topic_id, user_id),
    FOREIGN KEY (topic_id) REFERENCES topics(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Topic 댓글 (User Service 경계의 user_id에는 DB FK를 걸지 않음)
CREATE TABLE IF NOT EXISTS topic_comments (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    topic_id     BIGINT        NOT NULL,
    user_id      BIGINT        NOT NULL,
    author_name  VARCHAR(100)  NOT NULL,
    content      VARCHAR(1000) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (topic_id) REFERENCES topics(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 사용자의 관심 게임 (MSA 경계를 넘는 ID이므로 users/games에 DB FK를 걸지 않음)
CREATE TABLE IF NOT EXISTS user_games (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    game_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_game (user_id, game_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

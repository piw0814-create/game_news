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
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255)    NOT NULL UNIQUE,
    publisher   VARCHAR(255),
    genre       VARCHAR(100),
    platform                VARCHAR(255),
    image_url               VARCHAR(1000),
    registration_source     VARCHAR(20)     NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL | AI',
    review_status           VARCHAR(30)     NOT NULL DEFAULT 'CONFIRMED' COMMENT 'CONFIRMED | AI_CREATED | REVIEW_REQUIRED',
    registration_confidence DECIMAL(5,4),
    source_article_id       BIGINT,
    created_at              DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id)
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
    category         VARCHAR(30)     COMMENT 'RELEASE | UPDATE | INDUSTRY | ESPORTS | EVENT | CONTROVERSY | OTHER',
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
    created_at        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_article_game (article_id, game_id),
    FOREIGN KEY (article_id) REFERENCES news_articles(id),
    FOREIGN KEY (game_id) REFERENCES games(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Topic과 기사의 N:M 관계
CREATE TABLE IF NOT EXISTS topic_articles (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    topic_id    BIGINT      NOT NULL,
    article_id  BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_topic_article (topic_id, article_id),
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

-- 사용자의 관심 게임 (MSA 경계를 넘는 ID이므로 users/games에 DB FK를 걸지 않음)
CREATE TABLE IF NOT EXISTS user_games (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    game_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_game (user_id, game_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- TopicFranchise 2단계 마이그레이션
-- 기존 환경에서 Topic과 Franchise 관계 테이블을 추가한다.

CREATE TABLE IF NOT EXISTS topic_franchises (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    topic_id         BIGINT          NOT NULL,
    franchise_id     BIGINT          NOT NULL,
    is_primary       BOOLEAN         NOT NULL DEFAULT FALSE,
    relevance_score  DECIMAL(5,4),
    created_at       DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_topic_franchise (topic_id, franchise_id),
    KEY idx_topic_franchise_topic_id (topic_id),
    KEY idx_topic_franchise_franchise_id (franchise_id),
    CONSTRAINT fk_topic_franchise_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
    CONSTRAINT fk_topic_franchise_franchise FOREIGN KEY (franchise_id) REFERENCES franchises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기존 ArticleFranchise가 이미 연결된 Topic도 소급 반영
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
GROUP BY ta.topic_id, af.franchise_id;

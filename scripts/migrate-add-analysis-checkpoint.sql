-- Article Analyzer 결과를 파이프라인 체크포인트로 보존한다.
-- ANALYZED/TOPIC_PENDING 상태의 Recovery가 Article Analyzer를 다시 호출하지 않고
-- Entity Resolution 또는 Topic Integration 단계부터 재개할 수 있게 한다.

ALTER TABLE news_articles
    ADD COLUMN IF NOT EXISTS initial_topic_title VARCHAR(500) NULL AFTER entity_type,
    ADD COLUMN IF NOT EXISTS semantic_importance_score INT NULL AFTER initial_topic_title,
    ADD COLUMN IF NOT EXISTS initial_why_important LONGTEXT NULL AFTER semantic_importance_score,
    ADD COLUMN IF NOT EXISTS analysis_checkpoint LONGTEXT NULL AFTER initial_why_important;

-- analysis_status는 VARCHAR 기반 Enum 저장 컬럼이므로 ANALYZED/TOPIC_PENDING 추가에 별도 DDL이 필요 없다.

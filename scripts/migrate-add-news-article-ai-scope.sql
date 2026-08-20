-- NewsArticle에 기사 자체의 AI 범위 판정 결과를 저장한다.
-- 기존 Topic 귀속 기사는 게임 뉴스 관련성이 이미 검증된 것으로 안전하게 backfill한다.
-- Topic이 없는 기존 COMPLETED 기사는 추정하지 않고 NULL로 남겨 재분석 대상으로 판단한다.

ALTER TABLE news_articles
    ADD COLUMN IF NOT EXISTS game_news_relevant BOOLEAN NULL AFTER category,
    ADD COLUMN IF NOT EXISTS entity_type VARCHAR(30) NULL AFTER game_news_relevant;

UPDATE news_articles a
SET a.game_news_relevant = TRUE
WHERE a.game_news_relevant IS NULL
  AND EXISTS (
      SELECT 1
      FROM topic_articles ta
      WHERE ta.article_id = a.id
  );

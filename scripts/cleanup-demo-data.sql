-- 14-6 발표 전 테스트 데이터 정리
-- 실행 대상: game_news_db
-- 실제 Collector/AI 데이터(Phantom Blade Zero / PC Gamer)와 Game #1 GTA VI는 유지한다.

START TRANSACTION;

-- 삭제 대상 테스트 기사 URL
CREATE TEMPORARY TABLE cleanup_news_urls (url VARCHAR(1000));
INSERT INTO cleanup_news_urls (url) VALUES
  ('https://example.com/gta6-release-001'),
  ('https://example.com/kafka-test-20260818-0843'),
  ('https://example.com/ai-analysis-test-20260818-1009'),
  ('https://example.com/topic-auto-test-a-20260818'),
  ('https://example.com/topic-auto-test-b-20260818'),
  ('https://example.com/topic-auto-test-c-20260818'),
  ('https://example.com/topic-auto-test-d-20260818');

-- 관계 테이블을 먼저 정리한다.
DELETE ta
FROM topic_articles ta
JOIN topics t ON t.id = ta.topic_id
WHERE t.title IN (
  'GTA VI 출시 일정 발표',
  'GTA VI 출시 시기 관련 추가 설명'
);

DELETE ta
FROM topic_articles ta
JOIN news_articles n ON n.id = ta.article_id
JOIN cleanup_news_urls c ON c.url = n.url;

DELETE tg
FROM topic_games tg
JOIN topics t ON t.id = tg.topic_id
WHERE t.title IN (
  'GTA VI 출시 일정 발표',
  'GTA VI 출시 시기 관련 추가 설명'
);

DELETE ag
FROM article_games ag
JOIN news_articles n ON n.id = ag.article_id
JOIN cleanup_news_urls c ON c.url = n.url;

-- 수동/자동 Topic 테스트 데이터 삭제.
DELETE FROM topics
WHERE title IN (
  'GTA VI 출시 일정 발표',
  'GTA VI 출시 시기 관련 추가 설명'
);

-- AI/Kafka/Topic 통합 테스트 기사 삭제.
DELETE n
FROM news_articles n
JOIN cleanup_news_urls c ON c.url = n.url;

-- Gateway 차단 검증 중 생성된 게임 삭제.
DELETE ag
FROM article_games ag
JOIN games g ON g.id = ag.game_id
WHERE g.name = 'Should Not Be Created';

DELETE tg
FROM topic_games tg
JOIN games g ON g.id = tg.game_id
WHERE g.name = 'Should Not Be Created';

DELETE ug
FROM user_games ug
JOIN games g ON g.id = ug.game_id
WHERE g.name = 'Should Not Be Created';

DELETE FROM games
WHERE name = 'Should Not Be Created';

DROP TEMPORARY TABLE cleanup_news_urls;
COMMIT;

-- 확인용 조회
SELECT id, name FROM games ORDER BY id;
SELECT id, title FROM topics ORDER BY id;
SELECT id, title, source_name, analysis_status FROM news_articles ORDER BY id;

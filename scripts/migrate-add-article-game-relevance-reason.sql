-- ArticleGame AI 관련성 판단 근거 저장
-- 현재 news-service는 ddl-auto=update이므로 일반 Docker 재기동 시 자동 반영된다.
-- 운영 환경에서 ddl-auto를 끈 경우에만 이 스크립트를 수동 적용한다.
ALTER TABLE article_games
    ADD COLUMN IF NOT EXISTS relevance_reason TEXT NULL AFTER confidence_score;

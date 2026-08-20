-- IGDB Franchise catalog sync
-- 기존 관계는 출처를 확정할 수 없으므로 relation_source를 NULL로 유지하여 자동 삭제 대상에서 보호한다.

ALTER TABLE games
    ADD COLUMN IF NOT EXISTS igdb_game_type VARCHAR(100) NULL AFTER igdb_id,
    ADD COLUMN IF NOT EXISTS version_parent_igdb_id BIGINT NULL AFTER igdb_game_type;

ALTER TABLE franchises
    ADD COLUMN IF NOT EXISTS last_synced_at DATETIME(6) NULL AFTER metadata_source;

ALTER TABLE game_franchises
    ADD COLUMN IF NOT EXISTS relation_source VARCHAR(20) NULL AFTER is_primary;

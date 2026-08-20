-- IGDB-first Game identity migration
-- Game.name은 표시/검색용이고, 외부 카탈로그 식별자는 games.igdb_id(UNIQUE)를 사용한다.
-- 기존 DB의 games.name UNIQUE 인덱스만 제거한다. igdb_id UNIQUE는 유지한다.

SET @game_name_unique_index := (
    SELECT INDEX_NAME
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'games'
      AND COLUMN_NAME = 'name'
      AND NON_UNIQUE = 0
      AND INDEX_NAME <> 'PRIMARY'
    ORDER BY INDEX_NAME
    LIMIT 1
);

SET @drop_game_name_unique_sql := IF(
    @game_name_unique_index IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE games DROP INDEX `', REPLACE(@game_name_unique_index, '`', '``'), '`')
);

PREPARE drop_game_name_unique_stmt FROM @drop_game_name_unique_sql;
EXECUTE drop_game_name_unique_stmt;
DEALLOCATE PREPARE drop_game_name_unique_stmt;

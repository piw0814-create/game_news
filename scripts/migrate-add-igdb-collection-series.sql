-- K: IGDB Collection(Series)를 로컬 Franchise identity로 사용할 수 있도록 외부 ID를 분리 저장한다.
-- /franchises/{id}와 /collections/{id}의 숫자 ID 공간은 서로 다르므로 같은 컬럼에 섞지 않는다.
ALTER TABLE franchises
    ADD COLUMN IF NOT EXISTS igdb_collection_id BIGINT NULL AFTER igdb_id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_franchises_igdb_collection_id
    ON franchises (igdb_collection_id);

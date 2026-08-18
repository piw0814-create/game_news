-- 2-B2B: 자체 JWT 인증 전환 후 더 이상 사용하지 않는 사용자 Role 컬럼 제거
-- 기존 사용자 id/email/password/name/timestamp 데이터는 유지한다.
ALTER TABLE users DROP COLUMN IF EXISTS role;

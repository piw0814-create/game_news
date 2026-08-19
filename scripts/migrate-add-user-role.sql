-- Existing game_news DB migration: add USER / ADMIN role support.
-- Run once before rebuilding user-service when upgrading an existing database.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER'
    COMMENT 'USER | ADMIN' AFTER name;

UPDATE users
SET role = 'USER'
WHERE role IS NULL OR TRIM(role) = '';

-- Promote the chosen account separately after confirming the target email:
-- UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';

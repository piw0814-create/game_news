-- Existing DB migration: remove the legacy AI_CREATED review state.
-- registration_source='AI' and registration_confidence preserve how the Game was created.

UPDATE games
SET review_status = 'CONFIRMED'
WHERE review_status = 'AI_CREATED';

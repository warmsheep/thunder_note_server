-- Media fields for multimedia messages (idempotent)
ALTER TABLE messages ADD COLUMN IF NOT EXISTS media_type VARCHAR(16);
ALTER TABLE messages ADD COLUMN IF NOT EXISTS media_url VARCHAR(512);
ALTER TABLE messages ADD COLUMN IF NOT EXISTS media_duration INTEGER;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(512);
ALTER TABLE messages ADD COLUMN IF NOT EXISTS file_name VARCHAR(256);
ALTER TABLE messages ADD COLUMN IF NOT EXISTS file_size BIGINT;

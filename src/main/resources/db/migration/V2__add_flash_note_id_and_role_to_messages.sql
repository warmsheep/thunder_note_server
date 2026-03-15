-- V2: Add flash_note_id and role columns to messages table for flash-note chat flow
-- This enables note-scoped messages while still associating data with current authenticated user

ALTER TABLE messages ADD COLUMN IF NOT EXISTS flash_note_id BIGINT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS role VARCHAR(32);

-- Index for querying messages by flash_note_id
CREATE INDEX IF NOT EXISTS idx_messages_flash_note_id ON messages(flash_note_id);

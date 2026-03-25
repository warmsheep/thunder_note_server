ALTER TABLE messages ADD COLUMN IF NOT EXISTS client_request_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_messages_client_request_id ON messages(client_request_id);

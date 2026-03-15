CREATE TABLE IF NOT EXISTS favorite_messages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_favorite_messages_user_message UNIQUE (user_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_favorite_messages_user_id ON favorite_messages(user_id);
CREATE INDEX IF NOT EXISTS idx_favorite_messages_message_id ON favorite_messages(message_id);

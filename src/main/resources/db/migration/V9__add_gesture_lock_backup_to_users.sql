ALTER TABLE users
    ADD COLUMN IF NOT EXISTS gesture_lock_ciphertext TEXT,
    ADD COLUMN IF NOT EXISTS gesture_lock_nonce VARCHAR(255),
    ADD COLUMN IF NOT EXISTS gesture_lock_kdf_params TEXT,
    ADD COLUMN IF NOT EXISTS gesture_lock_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS gesture_lock_updated_at TIMESTAMP;

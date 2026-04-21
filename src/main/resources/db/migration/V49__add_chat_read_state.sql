ALTER TABLE users
    ADD COLUMN last_read_global_message_id BIGINT NULL,
    ADD COLUMN last_read_global_at TIMESTAMPTZ NULL;

ALTER TABLE group_member
    ADD COLUMN last_read_chat_message_id BIGINT NULL,
    ADD COLUMN last_read_chat_at TIMESTAMPTZ NULL;

CREATE INDEX idx_chat_message_group_message_id
    ON chat_message (group_id, message_id);

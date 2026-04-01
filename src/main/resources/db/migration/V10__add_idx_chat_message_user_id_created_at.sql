CREATE INDEX idx_global_chat_message_user_created_at
    ON global_chat_message (user_id, created_at);

CREATE INDEX idx_chat_message_group_user_created_at
    ON chat_message (user_id, created_at);

ALTER TABLE group_member
ADD CONSTRAINT fk_group_member_user
FOREIGN KEY (user_id)
REFERENCES users(user_id)
ON DELETE CASCADE;
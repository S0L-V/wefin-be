-- published_at TIMESTAMP → TIMESTAMPTZ (OffsetDateTime 통일)
ALTER TABLE news_article
    ALTER COLUMN published_at TYPE TIMESTAMPTZ;

ALTER TABLE raw_news_article
    ALTER COLUMN original_published_at TYPE TIMESTAMPTZ;

ALTER TABLE chat_message
    ADD COLUMN reply_to_message_id BIGINT NULL;

ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_reply_to
        FOREIGN KEY (reply_to_message_id) REFERENCES chat_message(message_id);
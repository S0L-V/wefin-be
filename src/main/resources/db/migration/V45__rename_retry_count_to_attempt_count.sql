ALTER TABLE email_send_log
    RENAME COLUMN retry_count TO attempt_count;
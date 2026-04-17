ALTER TABLE email_verification
    ADD COLUMN resend_window_started_at TIMESTAMPTZ;

ALTER TABLE email_verification
    ADD COLUMN version BIGINT DEFAULT 0;
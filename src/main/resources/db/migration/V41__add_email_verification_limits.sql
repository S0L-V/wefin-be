ALTER TABLE email_verification
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;

ALTER TABLE email_verification
    ADD COLUMN resend_count INT NOT NULL DEFAULT 0;

ALTER TABLE email_verification
    ADD COLUMN locked_until TIMESTAMPTZ;

ALTER TABLE email_verification
    ADD COLUMN last_sent_at TIMESTAMPTZ;
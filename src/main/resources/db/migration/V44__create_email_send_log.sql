CREATE TABLE email_send_log (
                                id BIGSERIAL PRIMARY KEY,
                                email VARCHAR(100) NOT NULL,
                                purpose VARCHAR(30) NOT NULL,
                                code VARCHAR(20) NOT NULL,
                                status VARCHAR(20) NOT NULL,
                                retry_count INT NOT NULL,
                                last_tried_at TIMESTAMPTZ,
                                created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE email_send_log
    ADD CONSTRAINT chk_email_send_log_status
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAIL'));
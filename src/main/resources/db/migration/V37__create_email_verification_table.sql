CREATE TABLE email_verification (
                                    email_verification_id BIGSERIAL PRIMARY KEY,

                                    email VARCHAR(100) NOT NULL,
                                    purpose VARCHAR(30) NOT NULL,

                                    verification_code VARCHAR(20) NOT NULL,
                                    verified BOOLEAN NOT NULL DEFAULT FALSE,

                                    expires_at TIMESTAMPTZ NOT NULL,

                                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- email + purpose 유니크
ALTER TABLE email_verification
    ADD CONSTRAINT uk_email_verification_email_purpose
        UNIQUE (email, purpose);
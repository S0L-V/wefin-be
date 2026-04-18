CREATE TABLE recommended_news_card (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             UUID        NOT NULL,
    card_type           VARCHAR(10) NOT NULL
                        CONSTRAINT chk_recommended_card_type CHECK (card_type IN ('STOCK', 'SECTOR')),
    interest_code       VARCHAR(50) NOT NULL,
    interest_name       VARCHAR(100) NOT NULL,
    title               TEXT        NOT NULL,
    summary             TEXT        NOT NULL,
    context             TEXT        NOT NULL,
    reasons             JSONB       NOT NULL,
    linked_cluster_id   BIGINT      NOT NULL,
    interest_hash       VARCHAR(64) NOT NULL,
    session_started_at  TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_recommended_card_user
    ON recommended_news_card (user_id, created_at DESC);

CREATE INDEX idx_recommended_card_user_type
    ON recommended_news_card (user_id, card_type, created_at DESC);

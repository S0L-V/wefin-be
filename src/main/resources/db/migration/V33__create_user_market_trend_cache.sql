-- /api/market-trends/personalized 응답을 사용자 단위로 하루 1회 캐싱한다.
-- 다음 호출 시 cache hit이면 OpenAI 재호출 없이 즉시 반환하여 비용/지연을 줄인다.
-- 관심사 변경 시(InterestService/WatchlistService add·delete) 해당 사용자의 오늘자 row를 삭제해 무효화한다.
--
-- 캐싱 대상: personalized=true 결과만 저장한다. personalized=false 폴백은 다음 호출 때 다시 시도하기 위해 저장하지 않는다

CREATE TABLE IF NOT EXISTS user_market_trend (
    user_market_trend_id BIGSERIAL PRIMARY KEY,
    user_id              UUID         NOT NULL,
    trend_date           DATE         NOT NULL,
    mode                 VARCHAR(30)  NOT NULL DEFAULT 'MATCHED',
    summary              TEXT         NOT NULL,
    insight_cards        JSONB        NOT NULL,
    related_keywords     JSONB        NOT NULL,
    source_cluster_ids   JSONB        NOT NULL,
    source_article_count INT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_market_trend_user_date UNIQUE (user_id, trend_date),
    CONSTRAINT chk_user_market_trend_mode CHECK (mode IN ('MATCHED', 'ACTION_BRIEFING'))
);

CREATE INDEX IF NOT EXISTS idx_user_market_trend_user_date ON user_market_trend (user_id, trend_date);

ALTER TABLE user_market_trend
    ADD COLUMN IF NOT EXISTS mode VARCHAR(30) NOT NULL DEFAULT 'MATCHED';

ALTER TABLE user_market_trend
    DROP CONSTRAINT IF EXISTS chk_user_market_trend_mode;

ALTER TABLE user_market_trend
    ADD CONSTRAINT chk_user_market_trend_mode
        CHECK (mode IN ('MATCHED', 'ACTION_BRIEFING'));

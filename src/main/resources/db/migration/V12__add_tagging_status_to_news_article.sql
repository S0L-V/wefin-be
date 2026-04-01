-- news_article에 태깅 처리 상태 추적 컬럼 추가
ALTER TABLE news_article
    ADD COLUMN IF NOT EXISTS tagging_status        VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS tagging_retry_count   INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tagging_attempted_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tagging_error_message TEXT;

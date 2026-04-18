-- crawl_attempted_at 타입을 TIMESTAMP → TIMESTAMPTZ로 변경 (OffsetDateTime 통일)
ALTER TABLE news_article
    ALTER COLUMN crawl_attempted_at TYPE TIMESTAMPTZ;

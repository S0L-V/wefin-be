-- published_at TIMESTAMP → TIMESTAMPTZ (OffsetDateTime 통일)
ALTER TABLE news_article
    ALTER COLUMN published_at TYPE TIMESTAMPTZ;

ALTER TABLE raw_news_article
    ALTER COLUMN original_published_at TYPE TIMESTAMPTZ;

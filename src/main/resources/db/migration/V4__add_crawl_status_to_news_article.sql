ALTER TABLE "news_article" ADD COLUMN "crawl_status" VARCHAR(30) NOT NULL DEFAULT 'PENDING';
ALTER TABLE "news_article" ADD COLUMN "crawl_attempted_at" TIMESTAMP NULL;
ALTER TABLE "news_article" ADD COLUMN "crawl_retry_count" INT NOT NULL DEFAULT 0;
ALTER TABLE "news_article" ADD COLUMN "crawl_error_message" TEXT NULL;

COMMENT ON COLUMN "news_article"."crawl_status" IS 'PENDING / SUCCESS / FAILED / SKIPPED';
COMMENT ON COLUMN "news_article"."crawl_attempted_at" IS '마지막 크롤링 시도 시각';
COMMENT ON COLUMN "news_article"."crawl_retry_count" IS '크롤링 재시도 횟수';
COMMENT ON COLUMN "news_article"."crawl_error_message" IS '크롤링 실패 메시지';

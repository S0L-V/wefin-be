ALTER TABLE "news_article" ADD COLUMN "updated_at" TIMESTAMP NULL;
ALTER TABLE "news_source" ADD COLUMN "updated_at" TIMESTAMP NULL;

COMMENT ON COLUMN "news_article"."updated_at" IS '수정 시각';
COMMENT ON COLUMN "news_source"."updated_at" IS '수정 시각';

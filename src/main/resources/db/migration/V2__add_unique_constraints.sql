CREATE UNIQUE INDEX IF NOT EXISTS "uk_raw_news_article_original_url" ON "raw_news_article" ("original_url");
CREATE UNIQUE INDEX IF NOT EXISTS "uk_news_source_source_name" ON "news_source" ("source_name");

ALTER TABLE "users"
    ADD CONSTRAINT "uk_users_email" UNIQUE ("email");
ALTER TABLE "users"
    ADD CONSTRAINT "uk_users_nickname" UNIQUE ("nickname");
ALTER TABLE "payment"
    ADD CONSTRAINT "uk_payment_order_id" UNIQUE ("order_id");
CREATE UNIQUE INDEX "uk_payment_provider_payment_key"
    ON "payment" ("provider_payment_key")
    WHERE "provider_payment_key" IS NOT NULL;
CREATE UNIQUE INDEX "uk_subscription_user_active"
    ON "subscription" ("user_id")
    WHERE "status" = 'ACTIVE';
ALTER TABLE GAME_ROOM
    ADD COLUMN started_at TIMESTAMPTZ NULL;
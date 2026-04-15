ALTER TABLE "payment"
    ADD CONSTRAINT "chk_payment_status"
        CHECK ("status" IN ('READY', 'PAID', 'FAILED', 'CANCELED'));

ALTER TABLE "payment"
    ADD CONSTRAINT "chk_payment_provider"
        CHECK ("provider" IN ('TOSS'));

ALTER TABLE "payment"
    ADD CONSTRAINT "chk_payment_amount_positive"
        CHECK ("amount" > 0);

ALTER TABLE "subscription"
    ADD CONSTRAINT "chk_subscription_status"
        CHECK ("status" IN ('ACTIVE', 'CANCELED', 'EXPIRED'));

ALTER TABLE "subscription"
    ADD CONSTRAINT "chk_subscription_date_order"
        CHECK ("expired_at" IS NULL OR "expired_at" > "started_at");

ALTER TABLE "subscription_plan"
    ADD CONSTRAINT "chk_subscription_plan_price_positive"
        CHECK ("price" > 0);

ALTER TABLE "subscription_plan"
    ADD CONSTRAINT "chk_subscription_plan_billing_cycle"
        CHECK ("billing_cycle" IN ('MONTHLY', 'YEARLY'));

CREATE TABLE "payment_failure_log" (
                                       "payment_failure_log_id" BIGSERIAL PRIMARY KEY,
                                       "payment_id" BIGINT,
                                       "user_id" UUID NOT NULL,
                                       "order_id" VARCHAR(100),
                                       "payment_key" VARCHAR(200),
                                       "stage" VARCHAR(50) NOT NULL,
                                       "error_code" VARCHAR(100) NOT NULL,
                                       "error_message" VARCHAR(500) NOT NULL,
                                       "logged_at" TIMESTAMPTZ NOT NULL
);

ALTER TABLE "payment_failure_log"
    ADD CONSTRAINT "fk_payment_failure_log_payment"
        FOREIGN KEY ("payment_id")
            REFERENCES "payment" ("payment_id");

ALTER TABLE "payment_failure_log"
    ADD CONSTRAINT "fk_payment_failure_log_user"
        FOREIGN KEY ("user_id")
            REFERENCES "users" ("user_id");

CREATE INDEX "idx_payment_failure_log_order_id"
    ON "payment_failure_log" ("order_id");

CREATE INDEX "idx_payment_failure_log_user_id"
    ON "payment_failure_log" ("user_id");
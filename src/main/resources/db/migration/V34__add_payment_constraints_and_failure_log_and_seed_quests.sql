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

INSERT INTO quest_template (
    code,
    title,
    description,
    complete_type,
    event_type,
    target_value,
    condition_json,
    reward,
    is_repeatable,
    is_active,
    created_at,
    updated_at
) VALUES
      (
          'LOGIN_DAILY',
          '오늘도 출석 완료',
          '앱에 접속해 오늘의 퀘스트를 시작해보세요.',
          'COUNT',
          'LOGIN',
          1,
          NULL,
          100000,
          true,
          true,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          'SHARE_NEWS_DAILY',
          '뉴스 공유하기',
          '관심 있는 뉴스를 채팅방에 공유해보세요.',
          'COUNT',
          'SHARE_NEWS',
          1,
          NULL,
          120000,
          true,
          true,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          'USE_AI_CHAT_DAILY',
          '위피니와 대화하기',
          'AI 채팅을 활용해 투자 아이디어를 탐색해보세요.',
          'COUNT',
          'USE_AI_CHAT',
          1,
          NULL,
          150000,
          true,
          true,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          'SEND_GROUP_CHAT_DAILY',
          '그룹 채팅 참여하기',
          '그룹 채팅방에서 다른 사용자와 대화를 나눠보세요.',
          'COUNT',
          'SEND_GROUP_CHAT',
          2,
          NULL,
          120000,
          true,
          true,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          'BUY_STOCK_DAILY',
          '매수 도전하기',
          '주식을 매수하고 투자 흐름에 참여해보세요.',
          'COUNT',
          'BUY_STOCK',
          1,
          NULL,
          180000,
          true,
          true,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          'JOIN_GAME_ROOM_DAILY',
          '게임방 참가하기',
          '게임방에 입장해 다른 사용자들과 함께 경쟁해보세요.',
          'COUNT',
          'JOIN_GAME_ROOM',
          1,
          NULL,
          140000,
          true,
          true,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          'CREATE_GAME_ROOM_DAILY',
          '게임방 직접 만들기',
          '새로운 게임방을 직접 생성해보세요.',
          'COUNT',
          'CREATE_GAME_ROOM',
          1,
          NULL,
          200000,
          true,
          true,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          'PROFIT_RATE_DAILY',
          '목표 수익률 달성하기',
          '오늘 목표 수익률을 달성해 보상을 받아보세요.',
          'PERCENT',
          'CHECK_PROFIT_RATE',
          5,
          NULL,
          250000,
          true,
          true,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          'GAME_RANK_DAILY',
          '게임방 상위권 달성하기',
          '오늘 게임방 순위를 달성해 보상을 받아보세요.',
          'PERCENT',
          'CHECK_GAME_RANK',
          3,
          NULL,
          220000,
          true,
          true,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      );

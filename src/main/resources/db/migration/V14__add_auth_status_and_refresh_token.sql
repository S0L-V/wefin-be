ALTER TABLE "users"
    ADD COLUMN "status" VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE "users"
    ADD CONSTRAINT "chk_users_status"
        CHECK ("status" IN ('ACTIVE', 'LOCKED', 'WITHDRAWN'));

CREATE TABLE "refresh_token" (
                                 "user_id" UUID NOT NULL,
                                 "token" VARCHAR(500) NOT NULL,
                                 "expires_at" TIMESTAMPTZ NOT NULL,
                                 "revoked" BOOLEAN NOT NULL DEFAULT FALSE,
                                 "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                 "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE "refresh_token"
    ADD CONSTRAINT "PK_REFRESH_TOKEN" PRIMARY KEY ("user_id");

ALTER TABLE "refresh_token"
    ADD CONSTRAINT "FK_user_TO_refresh_token_1"
        FOREIGN KEY ("user_id")
            REFERENCES "users" ("user_id");

ALTER TABLE "refresh_token"
    ADD CONSTRAINT "uk_refresh_token_token" UNIQUE ("token");

CREATE INDEX "idx_refresh_token_token"
    ON "refresh_token" ("token");
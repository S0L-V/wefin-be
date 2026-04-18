-- 1) groups 테이블에 group_type 추가
ALTER TABLE "groups"
    ADD COLUMN "group_type" VARCHAR(20) NOT NULL DEFAULT 'SHARED';

ALTER TABLE "groups"
    ADD CONSTRAINT "chk_groups_group_type"
        CHECK ("group_type" IN ('HOME', 'SHARED'));


-- 2) group_member status 제약 변경
ALTER TABLE "group_member"
DROP CONSTRAINT IF EXISTS "chk_group_member_status";

UPDATE "group_member"
SET "status" = 'INACTIVE'
WHERE "status" = 'LEFT';

ALTER TABLE "group_member"
    ADD CONSTRAINT "chk_group_member_status"
        CHECK ("status" IN ('ACTIVE', 'INACTIVE'));


-- 3) role 제약 재확인
ALTER TABLE "group_member"
DROP CONSTRAINT IF EXISTS "chk_group_member_role";

ALTER TABLE "group_member"
    ADD CONSTRAINT "chk_group_member_role"
        CHECK ("role" IN ('LEADER', 'MEMBER'));


-- 4) 동일 사용자-동일 그룹 중복 가입 방지 제약 재확인
ALTER TABLE "group_member"
DROP CONSTRAINT IF EXISTS "uk_group_member_user_group";

ALTER TABLE "group_member"
    ADD CONSTRAINT "uk_group_member_user_group"
        UNIQUE ("user_id", "group_id");


-- 5) 유저당 ACTIVE 그룹 1개만 허용
CREATE UNIQUE INDEX IF NOT EXISTS "uk_group_member_user_active"
    ON "group_member" ("user_id")
    WHERE "status" = 'ACTIVE';


-- 6) 채팅 뉴스 공유 테이블 추가
CREATE TABLE chat_message_news_share (
                                         chat_message_id BIGINT PRIMARY KEY,
                                         news_cluster_id BIGINT NOT NULL,
                                         shared_title VARCHAR(255) NOT NULL,
                                         shared_summary TEXT,
                                         shared_thumbnail_url TEXT,
                                         CONSTRAINT fk_chat_message_news_share_chat_message
                                             FOREIGN KEY (chat_message_id) REFERENCES chat_message (message_id) ON DELETE CASCADE,
                                         CONSTRAINT fk_chat_message_news_share_news_cluster
                                             FOREIGN KEY (news_cluster_id) REFERENCES news_cluster (news_cluster_id)
);

CREATE INDEX idx_chat_message_news_share_news_cluster_id
    ON chat_message_news_share (news_cluster_id);


-- 7) user_interest NOT NULL 제약 추가
ALTER TABLE "user_interest"
    ALTER COLUMN "interest_type" SET NOT NULL;

ALTER TABLE "user_interest"
    ALTER COLUMN "interest_value" SET NOT NULL;
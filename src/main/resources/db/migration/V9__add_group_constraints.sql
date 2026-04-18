ALTER TABLE "group_member"
    ADD CONSTRAINT "uk_group_member_user_group"
        UNIQUE ("user_id", "group_id");

ALTER TABLE "group_member"
    ADD CONSTRAINT "chk_group_member_role"
        CHECK ("role" IN ('LEADER', 'MEMBER'));

ALTER TABLE "group_member"
    ADD CONSTRAINT "chk_group_member_status"
        CHECK ("status" IN ('ACTIVE', 'LEFT'));

ALTER TABLE "group_invite"
    ADD CONSTRAINT "chk_group_invite_status"
        CHECK ("status" IN ('PENDING', 'ACCEPTED', 'EXPIRED'));
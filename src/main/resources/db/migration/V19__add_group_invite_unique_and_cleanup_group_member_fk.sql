-- group_invite invite_code UNIQUE 제약 추가
ALTER TABLE "group_invite"
    ADD CONSTRAINT "uk_group_invite_invite_code"
        UNIQUE ("invite_code");

-- 중복된 user FK 제거 (CASCADE 없는 기존 FK 삭제)
ALTER TABLE "group_member"
DROP CONSTRAINT "FK_user_TO_group_member_1";
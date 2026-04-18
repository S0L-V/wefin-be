-- 그룹당 방 1개 제한
CREATE UNIQUE INDEX ux_game_room_group_active
    ON game_room (group_id) WHERE status IN ('WAITING', 'IN_PROGRESS');

-- 방 중복 생성 방지
CREATE UNIQUE INDEX ux_game_room_host_active
    ON game_room (user_id) WHERE status IN ('WAITING', 'IN_PROGRESS');
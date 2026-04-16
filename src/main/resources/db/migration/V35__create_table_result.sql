CREATE TABLE game_result (
                             result_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                             room_id         UUID            NOT NULL REFERENCES game_room(room_id),
                             participant_id  UUID            NOT NULL REFERENCES game_participant(participant_id),
                             final_rank      INT             NOT NULL,
                             seed_money      DECIMAL(18,2)   NOT NULL,
                             final_asset     DECIMAL(18,2)   NOT NULL,
                             profit_rate     DECIMAL(8,2)    NOT NULL,
                             total_trades    INT             NOT NULL DEFAULT 0,
                             created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

                             CONSTRAINT uk_game_result_room_participant UNIQUE (room_id, participant_id)
);
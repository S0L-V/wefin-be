ALTER TABLE game_participant
DROP CONSTRAINT chk_participant_status;

ALTER TABLE game_participant
    ADD CONSTRAINT chk_participant_status
        CHECK (status IN ('ACTIVE', 'LEFT', 'FINISHED'));
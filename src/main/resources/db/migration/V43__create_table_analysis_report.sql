-- 1) 기존 FK 제거
ALTER TABLE game_analysis_report
DROP CONSTRAINT "FK_stock_daily_TO_game_analysis_report_1";

ALTER TABLE game_analysis_report
DROP CONSTRAINT "FK_game_participant_TO_game_analysis_report_1";

  -- 2) 기존 UNIQUE (daily_id, participant_id) 제거
ALTER TABLE game_analysis_report
DROP CONSTRAINT uk_report_daily_participant;

  -- 3) 사용하지 않을 컬럼 제거
ALTER TABLE game_analysis_report
DROP COLUMN daily_id,
      DROP COLUMN report_text;

  -- 4) 새 컬럼 추가
ALTER TABLE game_analysis_report
    ADD COLUMN performance TEXT NOT NULL,
      ADD COLUMN pattern     TEXT NOT NULL,
      ADD COLUMN suggestion  TEXT NOT NULL;

-- 5) DEFAULT 부여 (report_id, created_at은 코드에서 채우지 않고 DB가 자동 생성)
ALTER TABLE game_analysis_report
    ALTER COLUMN report_id  SET DEFAULT gen_random_uuid(),
ALTER COLUMN created_at SET DEFAULT now();

  -- 6) 새 FK (participant_id → game_participant)
ALTER TABLE game_analysis_report
    ADD CONSTRAINT fk_analysis_report_participant
        FOREIGN KEY (participant_id) REFERENCES game_participant(participant_id);

-- 7) 새 UNIQUE (참가자당 리포트 1개)
ALTER TABLE game_analysis_report
    ADD CONSTRAINT uk_analysis_report_participant UNIQUE (participant_id);
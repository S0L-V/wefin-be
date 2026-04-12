-- game_room의 seed 금액 컬럼을 소수점 2자리까지 저장할 수 있도록 변경
ALTER TABLE game_room
ALTER COLUMN seed TYPE DECIMAL(18,2) USING seed::DECIMAL(18,2);

-- game_participant의 seed 금액 컬럼을 소수점 2자리까지 저장할 수 있도록 변경
ALTER TABLE game_participant
ALTER COLUMN seed TYPE DECIMAL(18,2) USING seed::DECIMAL(18,2);

-- game_order 금액 관련 컬럼을 소수점 2자리까지 저장할 수 있도록 변경
ALTER TABLE game_order
ALTER COLUMN order_price TYPE DECIMAL(18,2) USING order_price::DECIMAL(18,2),
    ALTER COLUMN fee TYPE DECIMAL(18,2) USING fee::DECIMAL(18,2),
    ALTER COLUMN tax TYPE DECIMAL(18,2) USING tax::DECIMAL(18,2);

-- game_order 필수 컬럼에 NOT NULL 및 기본값 제약 추가
ALTER TABLE game_order
    ALTER COLUMN stock_name SET NOT NULL,
    ALTER COLUMN order_type SET NOT NULL,
    ALTER COLUMN order_price SET NOT NULL,
    ALTER COLUMN quantity SET NOT NULL,
    ALTER COLUMN fee SET NOT NULL,
    ALTER COLUMN tax SET NOT NULL,
    ALTER COLUMN tax SET DEFAULT 0;

-- game_holding의 평균 단가 컬럼을 소수점 2자리까지 저장할 수 있도록 변경
ALTER TABLE game_holding
    ALTER COLUMN avg_price TYPE DECIMAL(18,2) USING avg_price::DECIMAL(18,2);

-- game_holding의 quantity 기본값 제거
ALTER TABLE game_holding
    ALTER COLUMN quantity DROP DEFAULT;

-- game_portfolio_snapshot 금액 컬럼을 소수점 2자리까지 저장할 수 있도록 변경
ALTER TABLE game_portfolio_snapshot
ALTER COLUMN total_asset TYPE DECIMAL(18,2) USING total_asset::DECIMAL(18,2),
    ALTER COLUMN cash TYPE DECIMAL(18,2) USING cash::DECIMAL(18,2),
    ALTER COLUMN stock_value TYPE DECIMAL(18,2) USING stock_value::DECIMAL(18,2);

-- quest_template에 퀘스트 식별용 code 컬럼 추가
ALTER TABLE quest_template
    ADD COLUMN code VARCHAR(50);

-- code를 필수값으로 설정
ALTER TABLE quest_template
    ALTER COLUMN code SET NOT NULL;

-- code 중복 방지를 위한 유니크 제약 추가
ALTER TABLE quest_template
    ADD CONSTRAINT uq_quest_template_code UNIQUE (code);

-- 하루 공통 퀘스트를 저장할 daily_quest 테이블 생성
CREATE TABLE daily_quest (
                             daily_quest_id BIGSERIAL PRIMARY KEY,
                             template_id BIGINT NOT NULL,
                             quest_date DATE NOT NULL,
                             target_value INTEGER NOT NULL,
                             reward INTEGER NOT NULL,
                             created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_daily_quest_template
                                 FOREIGN KEY (template_id) REFERENCES quest_template(template_id),
                             CONSTRAINT uq_daily_quest_date_template
                                 UNIQUE (quest_date, template_id)
);

-- user_quest가 daily_quest를 참조할 수 있도록 컬럼 추가
ALTER TABLE user_quest
    ADD COLUMN daily_quest_id BIGINT;

-- 기존 user_quest -> quest_template 외래키 제거
ALTER TABLE user_quest
DROP CONSTRAINT "FK_quest_template_TO_user_quest_1";

-- user_quest와 daily_quest 연결용 외래키 제약 추가
ALTER TABLE user_quest
    ADD CONSTRAINT fk_user_quest_daily_quest
        FOREIGN KEY (daily_quest_id) REFERENCES daily_quest(daily_quest_id);

-- 같은 유저에게 같은 daily_quest가 중복 발급되지 않도록 유니크 제약 추가
ALTER TABLE user_quest
    ADD CONSTRAINT uq_user_quest_user_daily_quest UNIQUE (user_id, daily_quest_id);

ALTER TABLE user_quest
    ALTER COLUMN daily_quest_id SET NOT NULL;

-- user_quest에서 공통 퀘스트 정보 컬럼 제거
-- template/target/reward는 이제 daily_quest가 관리
ALTER TABLE user_quest
DROP COLUMN template_id,
DROP COLUMN target_value,
DROP COLUMN reward;

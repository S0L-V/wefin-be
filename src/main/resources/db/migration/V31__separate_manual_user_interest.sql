
-- 등록 경로(Watchlist/Interest API)가 TRUE로 저장한 row만 수동 등록으로 간주한다
ALTER TABLE user_interest
    ADD COLUMN IF NOT EXISTS manual_registered BOOLEAN NOT NULL DEFAULT FALSE;

-- unique 제약 확장: 수동 등록 row와 피드백 추천 row가 같은 (user, type, code) 조합으로 공존할 수 있어야 한다.
-- Watchlist(manual=TRUE)에 종목 A 등록 + 같은 종목에 HELPFUL 피드백(manual=FALSE upsert) 동시 허용.
-- 기존 (user_id, interest_type, interest_value) unique는 이를 허용하지 않아 재등록 시 ON CONFLICT가
-- manual=TRUE row의 weight를 덮어쓰거나 INSERT가 깨질 수 있다.
ALTER TABLE user_interest
    DROP CONSTRAINT IF EXISTS uk_user_interest_user_type_value;

-- 재실행 안전성: 이전 실행이 이 제약을 남긴 채 실패했을 수 있어 먼저 DROP IF EXISTS로 정리한다
ALTER TABLE user_interest
    DROP CONSTRAINT IF EXISTS uk_user_interest_user_type_value_manual;

ALTER TABLE user_interest
    ADD CONSTRAINT uk_user_interest_user_type_value_manual
        UNIQUE (user_id, interest_type, interest_value, manual_registered);

-- 관심사 allowlist 검증(NewsArticleTagRepository.existsByTagTypeAndTagCode)과 표시명 조회가
-- tag_type + tag_code 기준으로 매번 실행되므로 보조 인덱스를 추가한다.
CREATE INDEX IF NOT EXISTS idx_news_article_tag_type_code
    ON news_article_tag (tag_type, tag_code);

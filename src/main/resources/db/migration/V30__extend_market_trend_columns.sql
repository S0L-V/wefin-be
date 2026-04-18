-- 금융 동향(market_trend) 콘텐츠 확장
-- V1에서 뼈대만 있던 테이블에 AI 생성 결과를 담을 컬럼을 추가하고
-- 'DAILY' 세션을 허용하도록 체크 제약을 갱신한다
--
-- 저장 정책: (trend_date, session) 기준 upsert. 서비스 레이어에서 'DAILY' 고정값으로 사용하여
-- 하루 1건만 최신화한다. 기존 MORNING/EVENING 값도 호환을 위해 허용 목록에 유지

ALTER TABLE market_trend
    ADD COLUMN IF NOT EXISTS title                TEXT,
    ADD COLUMN IF NOT EXISTS summary              TEXT,
    ADD COLUMN IF NOT EXISTS insight_cards        JSONB,
    ADD COLUMN IF NOT EXISTS related_keywords     JSONB,
    -- 동향 생성에 사용된 클러스터 ID 목록 + 기사 총 개수 (출처 표시용).
    -- 클러스터 title 등 상세는 조회 시점에 news_cluster JOIN으로 보강
    ADD COLUMN IF NOT EXISTS source_cluster_ids   JSONB,
    ADD COLUMN IF NOT EXISTS source_article_count INT,
    ADD COLUMN IF NOT EXISTS updated_at           TIMESTAMPTZ NOT NULL DEFAULT now();

-- session 체크 제약을 DAILY까지 허용하도록 갱신
-- (V1의 chk_market_trend_session은 MORNING/EVENING만 허용했음)
ALTER TABLE market_trend DROP CONSTRAINT IF EXISTS chk_market_trend_session;
ALTER TABLE market_trend
    ADD CONSTRAINT chk_market_trend_session
        CHECK (session IN ('MORNING', 'EVENING', 'DAILY'));

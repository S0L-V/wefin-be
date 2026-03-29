-- market_snapshot을 market_trend에서 분리
-- 수집 단계에서는 MarketSnapshot을 독립 저장
-- MarketTrend는 세션별 요약/인사이트 그룹 용도로만 사용

-- 기존 FK 제약조건 제거
ALTER TABLE "market_snapshot"
    DROP CONSTRAINT IF EXISTS "FK_market_trend_TO_market_snapshot_1";

-- 기존 unique 제약조건 제거 (market_trend_id 포함)
ALTER TABLE "market_snapshot"
    DROP CONSTRAINT IF EXISTS "uk_market_snapshot_metric_type";

ALTER TABLE "market_snapshot"
    DROP CONSTRAINT IF EXISTS "uk_market_snapshot_display_order";

-- market_trend_id 컬럼 제거
ALTER TABLE "market_snapshot"
    DROP COLUMN "market_trend_id";

-- metric_type 단독 unique 제약조건 추가 (지표별 최신 값 1건 유지)
ALTER TABLE "market_snapshot"
    ADD CONSTRAINT "uk_market_snapshot_metric_type" UNIQUE ("metric_type");

-- display_order 컬럼 제거 (화면 순서는 프론트에서 metricType으로 결정)
ALTER TABLE "market_snapshot"
    DROP COLUMN "display_order";

-- updated_at 컬럼 추가
ALTER TABLE "market_snapshot"
    ADD COLUMN "updated_at" TIMESTAMPTZ;

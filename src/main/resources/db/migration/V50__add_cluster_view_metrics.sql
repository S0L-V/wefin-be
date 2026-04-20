-- news_cluster: 고유 사용자 기준 누적 조회 수 + 최근 시간 윈도우 조회 수
-- unique_viewer_count는 전체 기간 동안의 distinct user 수를 의미하며,
-- recent_view_count는 최근 N시간(기본 3h) 내 distinct user 수로 Hot 정렬에 사용
ALTER TABLE news_cluster
    ADD COLUMN unique_viewer_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN recent_view_count   BIGINT NOT NULL DEFAULT 0;

-- sort=view 피드 조회 성능을 위한 partial index
-- ACTIVE 상태의 클러스터만 대상으로 recent_view_count 기반 정렬을 최적화
-- (recent_view_count → published_at → news_cluster_id) 순으로 타이브레이커까지 포함한 안정 정렬
CREATE INDEX idx_news_cluster_hot
    ON news_cluster (recent_view_count DESC, published_at DESC, news_cluster_id DESC)
    WHERE status = 'ACTIVE';

-- 최근 시간 윈도우 집계(batch aggregation) 최적화를 위한 인덱스
-- read_at 기준 범위 스캔 후 news_cluster_id 단위 그룹 집계 성능 개선 목적
CREATE INDEX idx_user_news_cluster_read_at
    ON user_news_cluster_read (read_at, news_cluster_id);

-- Hot 집계 배치 실행 메타 정보 (singleton row, id=1 고정 UPSERT)
-- Micrometer가 상세 메트릭을 담당하며,
-- 본 테이블은 FE fallback 판단을 위한 최소 상태(last_success_at 등)만 유지
CREATE TABLE hot_aggregation_meta (
                                      id                 SMALLINT PRIMARY KEY CHECK (id = 1),
                                      last_success_at    TIMESTAMPTZ NOT NULL,
                                      last_window_start  TIMESTAMPTZ NOT NULL,
                                      last_updated_count INTEGER     NOT NULL,
                                      last_took_ms       INTEGER     NOT NULL
);
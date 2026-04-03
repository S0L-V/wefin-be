-- news_cluster에 클러스터링 파이프라인용 컬럼 추가

-- 클러스터 상태 (ACTIVE: 피드 노출, INACTIVE: 24시간 경과 비활성화)
ALTER TABLE news_cluster
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

-- AI 요약 상태 (PENDING: 미생성, GENERATED: 생성 완료, STALE: 재생성 필요)
ALTER TABLE news_cluster
    ADD COLUMN IF NOT EXISTS summary_status VARCHAR(30) NOT NULL DEFAULT 'PENDING';

-- 클러스터 centroid 벡터 (매칭 기준, 대표 기사와 분리)
ALTER TABLE news_cluster
    ADD COLUMN IF NOT EXISTS centroid_vector vector(1536);

-- 클러스터 내 기사 수
ALTER TABLE news_cluster
    ADD COLUMN IF NOT EXISTS article_count INT NOT NULL DEFAULT 0;

-- title NOT NULL 제약 완화 (AI 요약 생성 전에는 null)
ALTER TABLE news_cluster
    ALTER COLUMN title DROP NOT NULL;

-- TIMESTAMP → TIMESTAMPTZ 통일
ALTER TABLE news_cluster
    ALTER COLUMN published_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

-- news_cluster_article에 suspicious 플래그 추가 (soft scoring 결과 점수가 60~80인 기사)
ALTER TABLE news_cluster_article
    ADD COLUMN IF NOT EXISTS suspicious BOOLEAN NOT NULL DEFAULT FALSE;

-- news_cluster_article TIMESTAMP → TIMESTAMPTZ
ALTER TABLE news_cluster_article
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;

-- 인덱스: ACTIVE 클러스터 조회용
CREATE INDEX IF NOT EXISTS idx_news_cluster_status ON news_cluster (status);

-- 인덱스: summary_status 기반 요약 대상 조회용
CREATE INDEX IF NOT EXISTS idx_news_cluster_summary_status ON news_cluster (summary_status);

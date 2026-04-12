-- 인기 태그 쿼리 성능 개선 인덱스
-- findPopularTags: NewsArticleTag × NewsClusterArticle × NewsCluster 3-way JOIN + GROUP BY

-- news_cluster(status, summary_status) 복합 인덱스 — 피드 노출 대상 필터링
CREATE INDEX IF NOT EXISTS idx_news_cluster_status_summary
    ON news_cluster (status, summary_status);

-- news_article_tag(tag_type, news_article_id) 복합 인덱스 — 태그 유형별 조회
CREATE INDEX IF NOT EXISTS idx_news_article_tag_type_article
    ON news_article_tag (tag_type, news_article_id);

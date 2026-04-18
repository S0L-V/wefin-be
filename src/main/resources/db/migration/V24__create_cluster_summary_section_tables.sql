-- 클러스터 요약 섹션 테이블
CREATE TABLE cluster_summary_section (
    cluster_summary_section_id  BIGSERIAL       PRIMARY KEY,
    news_cluster_id             BIGINT          NOT NULL REFERENCES news_cluster(news_cluster_id),
    section_order               INT             NOT NULL,
    heading                     TEXT            NOT NULL,
    body                        TEXT            NOT NULL,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uk_cluster_summary_section_cluster_order UNIQUE (news_cluster_id, section_order)
);

-- 섹션 ↔ 근거 기사 매핑 테이블
CREATE TABLE cluster_summary_section_source (
    cluster_summary_section_id  BIGINT          NOT NULL REFERENCES cluster_summary_section(cluster_summary_section_id) ON DELETE CASCADE,
    news_article_id             BIGINT          NOT NULL REFERENCES news_article(news_article_id),
    PRIMARY KEY (cluster_summary_section_id, news_article_id)
);

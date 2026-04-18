-- news_article에 금융 관련성 판정 컬럼 추가
-- 서비스 범위가 금융 전용이므로, 태깅 단계에서 AI가 관련성을 판정하고
-- 후속 파이프라인(임베딩/클러스터링)에서 IRRELEVANT 기사는 제외한다.
-- 기존 데이터는 PENDING으로 백필되며 관리자 재판정 트리거로 점진적으로 분류된다.

ALTER TABLE news_article
    ADD COLUMN IF NOT EXISTS relevance VARCHAR(30) NOT NULL DEFAULT 'PENDING';

CREATE INDEX IF NOT EXISTS idx_news_article_relevance ON news_article (relevance);

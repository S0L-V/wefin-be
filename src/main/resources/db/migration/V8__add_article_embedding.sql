-- 기존 V1에서 만든 article_embedding 테이블 제거 (컬럼 구조 변경)
-- CASCADE로 참조하는 FK 제약조건만 제거됨 (참조 테이블 자체는 유지)
DROP TABLE IF EXISTS article_embedding CASCADE;

-- 청크별 임베딩 벡터 저장 테이블 (재생성)
CREATE TABLE article_embedding (
    article_embedding_id BIGSERIAL PRIMARY KEY,
    news_article_id      BIGINT       NOT NULL REFERENCES news_article(news_article_id),
    embedding_model      VARCHAR(50)  NOT NULL,
    embedding_version    VARCHAR(20)  NOT NULL,
    chunk_index          INT          NOT NULL,
    chunk_text           TEXT         NOT NULL,
    token_count          INT          NOT NULL,
    embedding            vector(1536) NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT uk_article_embedding_article_model_chunk
        UNIQUE (news_article_id, embedding_model, chunk_index)
);

-- news_article에 임베딩 처리 상태 추적 컬럼 추가
ALTER TABLE news_article
    ADD COLUMN IF NOT EXISTS embedding_status        VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS embedding_retry_count   INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS embedding_attempted_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS embedding_error_message TEXT;

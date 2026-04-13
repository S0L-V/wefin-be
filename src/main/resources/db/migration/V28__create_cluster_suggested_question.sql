-- 클러스터별 AI 추천 질문 테이블
CREATE TABLE cluster_suggested_question (
    cluster_suggested_question_id  BIGSERIAL    PRIMARY KEY,
    news_cluster_id                BIGINT       NOT NULL REFERENCES news_cluster(news_cluster_id) ON DELETE CASCADE,
    question_order                 INT          NOT NULL,
    question                       TEXT         NOT NULL,
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_cluster_question_order UNIQUE (news_cluster_id, question_order)
);

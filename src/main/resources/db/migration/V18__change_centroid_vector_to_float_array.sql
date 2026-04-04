-- vector → float8[] 변경
-- Hibernate가 vector 타입 읽기/쓰기 시 타입 변환 실패하는 문제 해결
-- cosine similarity를 Java 코드에서 계산하므로 pgvector 타입이 불필요

-- news_cluster.centroid_vector: vector → float8[]
ALTER TABLE news_cluster DROP COLUMN IF EXISTS centroid_vector;
ALTER TABLE news_cluster ADD COLUMN centroid_vector float8[];

-- article_embedding.embedding: vector → float8[] (text 경유 변환)
ALTER TABLE article_embedding
    ALTER COLUMN embedding TYPE float8[]
    USING (string_to_array(trim(both '[]' from embedding::text), ','))::float8[];

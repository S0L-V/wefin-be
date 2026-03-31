package com.solv.wefin.domain.news.embedding.entity;

import com.solv.wefin.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "article_embedding",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_article_embedding_article_model_chunk",
                columnNames = {"news_article_id", "embedding_model", "chunk_index"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleEmbedding extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_embedding_id")
    private Long id;

    @Column(name = "news_article_id", nullable = false)
    private Long newsArticleId;

    @Column(name = "embedding_model", nullable = false, length = 50)
    private String embeddingModel;

    @Column(name = "embedding_version", nullable = false, length = 20)
    private String embeddingVersion;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    @Builder
    private ArticleEmbedding(Long newsArticleId, String embeddingModel, String embeddingVersion,
                             int chunkIndex, String chunkText, int tokenCount, float[] embedding) {
        this.newsArticleId = newsArticleId;
        this.embeddingModel = embeddingModel;
        this.embeddingVersion = embeddingVersion;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.tokenCount = tokenCount;
        this.embedding = embedding;
    }
}

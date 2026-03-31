package com.solv.wefin.domain.news.embedding.service;

import com.solv.wefin.common.IntegrationTestBase;
import com.solv.wefin.domain.news.embedding.entity.ArticleEmbedding;
import com.solv.wefin.domain.news.embedding.repository.ArticleEmbeddingRepository;
import com.solv.wefin.domain.news.entity.NewsArticle;
import com.solv.wefin.domain.news.entity.NewsArticle.EmbeddingStatus;
import com.solv.wefin.domain.news.repository.NewsArticleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingPersistenceServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private EmbeddingPersistenceService persistenceService;

    @Autowired
    private ArticleEmbeddingRepository articleEmbeddingRepository;

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Test
    @DisplayName("임베딩 배치 저장 시 ArticleEmbedding이 저장되고 기사 상태가 SUCCESS로 변경된다")
    void saveEmbeddingsBatch_success() {
        // given
        NewsArticle article = createAndSaveArticle();
        article.markEmbeddingProcessing();
        newsArticleRepository.flush();

        float[] vector = new float[1536];
        ArticleEmbedding embedding = ArticleEmbedding.builder()
                .newsArticleId(article.getId())
                .embeddingModel("text-embedding-3-small")
                .embeddingVersion("v1")
                .chunkIndex(0)
                .chunkText("테스트 청크 텍스트")
                .tokenCount(10)
                .embedding(vector)
                .build();

        // when
        persistenceService.saveEmbeddingsBatch(List.of(embedding), List.of(article));

        // then
        List<ArticleEmbedding> saved = articleEmbeddingRepository
                .findByNewsArticleIdAndEmbeddingModel(article.getId(), "text-embedding-3-small");
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getChunkIndex()).isZero();
        assertThat(saved.get(0).getTokenCount()).isEqualTo(10);

        NewsArticle updated = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(updated.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.SUCCESS);
    }

    @Test
    @DisplayName("markEmbeddingFailed 호출 시 상태가 FAILED로 변경되고 에러 메시지와 retryCount가 저장된다")
    void markEmbeddingFailed_updatesStatus() {
        // given
        NewsArticle article = createAndSaveArticle();
        article.markEmbeddingProcessing();

        // when
        article.markEmbeddingFailed("API 호출 실패");
        newsArticleRepository.flush();

        // then
        NewsArticle updated = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(updated.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.FAILED);
        assertThat(updated.getEmbeddingErrorMessage()).isEqualTo("API 호출 실패");
        assertThat(updated.getEmbeddingRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 청크 임베딩을 한 번에 저장할 수 있다")
    void saveEmbeddingsBatch_multipleChunks() {
        // given
        NewsArticle article = createAndSaveArticle();
        article.markEmbeddingProcessing();
        newsArticleRepository.flush();

        float[] vector = new float[1536];
        List<ArticleEmbedding> embeddings = List.of(
                ArticleEmbedding.builder()
                        .newsArticleId(article.getId())
                        .embeddingModel("text-embedding-3-small")
                        .embeddingVersion("v1")
                        .chunkIndex(0).chunkText("첫 번째 청크").tokenCount(10).embedding(vector).build(),
                ArticleEmbedding.builder()
                        .newsArticleId(article.getId())
                        .embeddingModel("text-embedding-3-small")
                        .embeddingVersion("v1")
                        .chunkIndex(1).chunkText("두 번째 청크").tokenCount(15).embedding(vector).build()
        );

        // when
        persistenceService.saveEmbeddingsBatch(embeddings, List.of(article));

        // then
        List<ArticleEmbedding> saved = articleEmbeddingRepository
                .findByNewsArticleIdAndEmbeddingModel(article.getId(), "text-embedding-3-small");
        assertThat(saved).hasSize(2);
    }

    private NewsArticle createAndSaveArticle() {
        NewsArticle article = NewsArticle.builder()
                .rawNewsArticleId(null)
                .publisherName("test")
                .title("테스트 기사")
                .content("테스트 본문")
                .originalUrl("https://example.com/test-" + System.nanoTime())
                .dedupKey("key-" + System.nanoTime())
                .build();
        return newsArticleRepository.save(article);
    }
}

package com.solv.wefin.domain.news.tagging.service;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.entity.NewsArticle.TaggingStatus;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * markFailed()는 REQUIRES_NEW 전파를 사용하므로
 * 테스트 트랜잭션과 분리하여 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class TaggingMarkFailedIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("wefin_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        postgres.start();
    }

    @Autowired
    private TaggingPersistenceService persistenceService;

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Autowired
    private NewsArticleTagRepository newsArticleTagRepository;

    @AfterEach
    void cleanup() {
        newsArticleTagRepository.deleteAllInBatch();
        newsArticleRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("서비스의 markFailed 호출 시 상태가 FAILED로 변경되고 에러 메시지가 저장된다")
    void markFailed_updatesStatusThroughService() {
        // given
        NewsArticle article = createAndSaveProcessingArticle();

        // when
        persistenceService.markFailed(article.getId(), "API 호출 실패");

        // then
        NewsArticle updated = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(updated.getTaggingStatus()).isEqualTo(TaggingStatus.FAILED);
        assertThat(updated.getTaggingErrorMessage()).isEqualTo("API 호출 실패");
        assertThat(updated.getTaggingRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("서비스의 markFailed 호출 시 500자를 초과하는 에러 메시지가 잘린다")
    void markFailed_truncatesLongErrorMessage() {
        // given
        NewsArticle article = createAndSaveProcessingArticle();
        String longMessage = "E".repeat(600);

        // when
        persistenceService.markFailed(article.getId(), longMessage);

        // then
        NewsArticle updated = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(updated.getTaggingStatus()).isEqualTo(TaggingStatus.FAILED);
        assertThat(updated.getTaggingErrorMessage()).hasSize(500);
    }

    private NewsArticle createAndSaveProcessingArticle() {
        NewsArticle article = NewsArticle.builder()
                .rawNewsArticleId(null)
                .publisherName("test")
                .title("테스트 기사")
                .content("테스트 본문")
                .originalUrl("https://example.com/test-" + System.nanoTime())
                .dedupKey("key-" + System.nanoTime())
                .build();
        article = newsArticleRepository.saveAndFlush(article);
        article.markTaggingProcessing();
        return newsArticleRepository.saveAndFlush(article);
    }
}

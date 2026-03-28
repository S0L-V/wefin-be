package com.solv.wefin.domain.news.service;

import com.solv.wefin.domain.news.dto.CollectedNewsDto;
import com.solv.wefin.domain.news.entity.NewsCollectBatch;
import com.solv.wefin.domain.news.entity.NewsSource;
import com.solv.wefin.domain.news.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.repository.RawNewsArticleRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticlePersistenceServiceTest {

    @Mock
    private RawNewsArticleRepository rawNewsArticleRepository;

    @Mock
    private NewsArticleRepository newsArticleRepository;

    @InjectMocks
    private ArticlePersistenceService articlePersistenceService;

    @Test
    void processSingleArticle_checksDuplicateByOriginalUrlOnly() {
        CollectedNewsDto dto = sampleDto();

        when(rawNewsArticleRepository.existsByOriginalUrl(dto.getOriginalUrl())).thenReturn(true);

        articlePersistenceService.processSingleArticle(dto, sampleSource(), sampleBatch());

        verify(rawNewsArticleRepository).existsByOriginalUrl(dto.getOriginalUrl());
        verify(rawNewsArticleRepository, never()).saveAndFlush(any());
    }

    @Test
    void processSingleArticle_skipsOnlyOriginalUrlUniqueConstraintViolations() {
        CollectedNewsDto dto = sampleDto();
        SQLException sqlException = new SQLException(
                "duplicate key value violates unique constraint \"uk_raw_news_article_original_url\"");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate original_url",
                new ConstraintViolationException("duplicate original_url", sqlException, "uk_raw_news_article_original_url"));

        when(rawNewsArticleRepository.existsByOriginalUrl(dto.getOriginalUrl())).thenReturn(false);
        when(rawNewsArticleRepository.saveAndFlush(any())).thenThrow(exception);

        assertDoesNotThrow(() ->
                articlePersistenceService.processSingleArticle(dto, sampleSource(), sampleBatch()));

        verify(rawNewsArticleRepository).saveAndFlush(any());
        verify(newsArticleRepository, never()).save(any());
    }

    @Test
    void processSingleArticle_rethrowsNonDuplicateConstraintViolations() {
        CollectedNewsDto dto = sampleDto();
        SQLException sqlException = new SQLException(
                "insert or update on table \"raw_news_article\" violates foreign key constraint \"FK_news_collect_batch_TO_raw_news_article_1\"");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "foreign key violation",
                new ConstraintViolationException(
                        "foreign key violation",
                        sqlException,
                        "FK_news_collect_batch_TO_raw_news_article_1"));

        when(rawNewsArticleRepository.existsByOriginalUrl(dto.getOriginalUrl())).thenReturn(false);
        when(rawNewsArticleRepository.saveAndFlush(any())).thenThrow(exception);

        assertThrows(DataIntegrityViolationException.class, () ->
                articlePersistenceService.processSingleArticle(dto, sampleSource(), sampleBatch()));
    }

    private CollectedNewsDto sampleDto() {
        return CollectedNewsDto.builder()
                .externalArticleId("naver:123")
                .originalUrl("https://example.com/news/123")
                .originalTitle("sample title")
                .originalContent("sample content")
                .publisherName("example.com")
                .rawPayload("{\"id\":123}")
                .build();
    }

    private NewsSource sampleSource() {
        return NewsSource.builder()
                .sourceName("NaverNews")
                .sourceType(NewsSource.SourceType.API)
                .isActive(true)
                .build();
    }

    private NewsCollectBatch sampleBatch() {
        return NewsCollectBatch.builder()
                .newsSource(sampleSource())
                .requestedCategory(null)
                .build();
    }
}

package com.solv.wefin.domain.news.tagging.service;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.entity.NewsArticle.CrawlStatus;
import com.solv.wefin.domain.news.article.entity.NewsArticle.TaggingStatus;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.tagging.client.OpenAiTaggingClient;
import com.solv.wefin.domain.news.tagging.dto.TaggingResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaggingServiceTest {

    @Mock
    private NewsArticleRepository newsArticleRepository;
    @Mock
    private OpenAiTaggingClient openAiTaggingClient;
    @Mock
    private TaggingPersistenceService persistenceService;
    @Captor
    private ArgumentCaptor<List<NewsArticleTag>> tagsCaptor;
    @Captor
    private ArgumentCaptor<List<NewsArticle>> articlesCaptor;

    private TaggingService taggingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        taggingService = new TaggingService(newsArticleRepository, openAiTaggingClient, persistenceService);
    }

    @Test
    @DisplayName("대상 기사 3건이 있으면 태깅 → 저장을 수행한다")
    void tagPendingArticles_success() throws Exception {
        // given
        List<NewsArticle> articles = createArticles(3);
        stubFindTargets(articles);

        TaggingResult result = createTaggingResult();
        given(openAiTaggingClient.analyzeTags(anyString(), anyString())).willReturn(result);

        // when
        taggingService.tagPendingArticles();

        // then
        verify(persistenceService).markProcessing(articles);
        verify(persistenceService).saveTagsBatch(tagsCaptor.capture(), articlesCaptor.capture());
        assertThat(tagsCaptor.getValue()).isNotEmpty();
        assertThat(articlesCaptor.getValue()).hasSize(3);
        verify(persistenceService, never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("대상 기사가 없으면 아무 작업도 하지 않는다")
    void tagPendingArticles_noTargets() {
        // given
        stubFindTargets(List.of());

        // when
        taggingService.tagPendingArticles();

        // then
        verifyNoInteractions(openAiTaggingClient);
        verify(persistenceService, never()).markProcessing(anyList());
    }

    @Test
    @DisplayName("OpenAI API 실패 시 해당 기사만 FAILED로 마킹하고 나머지는 진행한다")
    void tagPendingArticles_partialFailure() throws Exception {
        // given
        List<NewsArticle> articles = createArticles(3);
        stubFindTargets(articles);

        TaggingResult result = createTaggingResult();
        given(openAiTaggingClient.analyzeTags(anyString(), anyString()))
                .willReturn(result)
                .willThrow(new RuntimeException("API 오류"))
                .willReturn(result);

        // when
        taggingService.tagPendingArticles();

        // then
        verify(persistenceService).markFailed(eq(2L), contains("API 오류"));
        verify(persistenceService).saveTagsBatch(tagsCaptor.capture(), articlesCaptor.capture());
        assertThat(articlesCaptor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("태깅 결과가 비어있으면 해당 기사를 FAILED로 마킹한다")
    void tagPendingArticles_emptyResult() throws Exception {
        // given
        List<NewsArticle> articles = createArticles(1);
        stubFindTargets(articles);

        TaggingResult emptyResult = new TaggingResult();
        given(openAiTaggingClient.analyzeTags(anyString(), anyString())).willReturn(emptyResult);

        // when
        taggingService.tagPendingArticles();

        // then
        verify(persistenceService).markFailed(eq(1L), contains("태깅 결과가 비어있습니다"));
        verify(openAiTaggingClient).analyzeTags(anyString(), anyString());
    }

    @Test
    @DisplayName("태깅 결과에서 STOCK/SECTOR/TOPIC 태그가 모두 생성된다")
    void tagPendingArticles_allTagTypes() throws Exception {
        // given
        List<NewsArticle> articles = createArticles(1);
        stubFindTargets(articles);

        TaggingResult result = createTaggingResult();
        given(openAiTaggingClient.analyzeTags(anyString(), anyString())).willReturn(result);

        // when
        taggingService.tagPendingArticles();

        // then
        verify(persistenceService).saveTagsBatch(tagsCaptor.capture(), any());
        List<NewsArticleTag> tags = tagsCaptor.getValue();
        assertThat(tags).extracting(NewsArticleTag::getTagType)
                .containsExactlyInAnyOrder(
                        NewsArticleTag.TagType.STOCK,
                        NewsArticleTag.TagType.SECTOR,
                        NewsArticleTag.TagType.TOPIC);
    }

    private void stubFindTargets(List<NewsArticle> articles) {
        given(newsArticleRepository.findTaggingTargets(
                eq(CrawlStatus.SUCCESS),
                eq(List.of(TaggingStatus.PENDING, TaggingStatus.FAILED)),
                eq(TaggingStatus.PROCESSING),
                eq(3), any(), any()))
                .willReturn(articles);
    }

    private List<NewsArticle> createArticles(int count) {
        List<NewsArticle> articles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            NewsArticle article = NewsArticle.builder()
                    .rawNewsArticleId(null)
                    .publisherName("test")
                    .title("테스트 기사 " + i)
                    .content("본문 내용 " + i)
                    .originalUrl("https://example.com/" + i)
                    .dedupKey("key" + i)
                    .build();
            ReflectionTestUtils.setField(article, "id", (long) (i + 1));
            ReflectionTestUtils.setField(article, "crawlStatus", CrawlStatus.SUCCESS);
            ReflectionTestUtils.setField(article, "taggingStatus", TaggingStatus.PENDING);
            articles.add(article);
        }
        return articles;
    }

    private TaggingResult createTaggingResult() throws Exception {
        String json = """
                {
                  "stocks": [{"code": "005930", "name": "삼성전자"}],
                  "sectors": [{"code": "SEMICONDUCTOR", "name": "반도체"}],
                  "topics": [{"code": "EARNINGS", "name": "실적"}]
                }
                """;
        return objectMapper.readValue(json, TaggingResult.class);
    }
}

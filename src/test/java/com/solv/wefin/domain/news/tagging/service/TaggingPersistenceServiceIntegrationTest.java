package com.solv.wefin.domain.news.tagging.service;

import com.solv.wefin.common.IntegrationTestBase;
import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.entity.NewsArticle.TaggingStatus;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaggingPersistenceServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TaggingPersistenceService persistenceService;

    @Autowired
    private NewsArticleTagRepository newsArticleTagRepository;

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Test
    @DisplayName("태그 배치 저장 시 태그가 저장되고 기사 상태가 SUCCESS로 변경된다")
    void saveTagsBatch_success() {
        // given
        NewsArticle article = createAndSaveArticle();
        article.markTaggingProcessing();
        newsArticleRepository.flush();

        List<NewsArticleTag> tags = List.of(
                NewsArticleTag.builder()
                        .newsArticleId(article.getId())
                        .tagType(TagType.STOCK)
                        .tagCode("005930")
                        .tagName("삼성전자")
                        .build(),
                NewsArticleTag.builder()
                        .newsArticleId(article.getId())
                        .tagType(TagType.SECTOR)
                        .tagCode("SEMICONDUCTOR")
                        .tagName("반도체")
                        .build()
        );

        // when
        persistenceService.saveTagsBatch(tags, List.of(article));

        // then
        List<NewsArticleTag> saved = newsArticleTagRepository.findByNewsArticleId(article.getId());
        assertThat(saved).hasSize(2);

        NewsArticle updated = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(updated.getTaggingStatus()).isEqualTo(TaggingStatus.SUCCESS);
    }

    @Test
    @DisplayName("재태깅 시 기존 태그가 삭제되고 새 태그로 교체된다")
    void saveTagsBatch_retagging() {
        // given
        NewsArticle article = createAndSaveArticle();

        // 기존 태그 저장
        newsArticleTagRepository.save(NewsArticleTag.builder()
                .newsArticleId(article.getId())
                .tagType(TagType.TOPIC)
                .tagCode("OLD_TOPIC")
                .tagName("이전 주제")
                .build());
        newsArticleTagRepository.flush();

        article.markTaggingProcessing();
        newsArticleRepository.flush();

        // 새 태그
        List<NewsArticleTag> newTags = List.of(
                NewsArticleTag.builder()
                        .newsArticleId(article.getId())
                        .tagType(TagType.TOPIC)
                        .tagCode("NEW_TOPIC")
                        .tagName("새 주제")
                        .build()
        );

        // when
        persistenceService.saveTagsBatch(newTags, List.of(article));

        // then
        List<NewsArticleTag> saved = newsArticleTagRepository.findByNewsArticleId(article.getId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTagCode()).isEqualTo("NEW_TOPIC");
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

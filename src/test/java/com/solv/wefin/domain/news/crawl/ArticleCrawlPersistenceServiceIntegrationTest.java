package com.solv.wefin.domain.news.crawl;

import com.solv.wefin.common.IntegrationTestBase;
import com.solv.wefin.domain.news.entity.NewsArticle;
import com.solv.wefin.domain.news.entity.NewsArticle.CrawlStatus;
import com.solv.wefin.domain.news.repository.NewsArticleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
class ArticleCrawlPersistenceServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ArticleCrawlPersistenceService persistenceService;

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void cleanup() {
        newsArticleRepository.deleteAll();
    }

    @Test
    void 크롤링_성공시_본문과_썸네일이_DB에_반영된다() {
        // given
        NewsArticle article = createAndSavePendingArticle("https://example.com/1");

        // when
        persistenceService.saveCrawlSuccess(article.getId(), "크롤링된 본문 내용", "https://example.com/thumb.jpg");

        // then
        NewsArticle updated = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(updated.getCrawlStatus()).isEqualTo(CrawlStatus.SUCCESS);
        assertThat(updated.getContent()).isEqualTo("크롤링된 본문 내용");
        assertThat(updated.getThumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
        assertThat(updated.getCrawlAttemptedAt()).isNotNull();
        assertThat(updated.getCrawlErrorMessage()).isNull();
        assertThat(updated.getCrawlRetryCount()).isEqualTo(0);
    }

    @Test
    void 크롤링_성공시_기존_썸네일이_있으면_덮어쓰지_않는다() {
        // given — 이미 썸네일이 저장된 기사
        NewsArticle article = createAndSavePendingArticle("https://example.com/2");
        persistenceService.saveCrawlSuccess(article.getId(), "첫 번째 본문", "https://example.com/original.jpg");

        // when — 다른 썸네일로 다시 저장 시도
        persistenceService.saveCrawlSuccess(article.getId(), "두 번째 본문", "https://example.com/new.jpg");

        // then — 기존 썸네일 유지, 본문은 갱신
        NewsArticle result = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(result.getThumbnailUrl()).isEqualTo("https://example.com/original.jpg");
        assertThat(result.getContent()).isEqualTo("두 번째 본문");
    }

    @Test
    void 성공했던_기사가_다시_성공하면_retryCount는_증가하지_않는다() {
        // given — 이미 성공한 기사
        NewsArticle article = createAndSavePendingArticle("https://example.com/2-1");
        persistenceService.saveCrawlSuccess(article.getId(), "첫 번째 본문", "https://example.com/thumb.jpg");

        // when — 다시 성공
        persistenceService.saveCrawlSuccess(article.getId(), "두 번째 본문", null);

        // then
        NewsArticle updated = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(updated.getCrawlRetryCount()).isEqualTo(0);
        assertThat(updated.getCrawlStatus()).isEqualTo(CrawlStatus.SUCCESS);
    }

    @Test
    void 크롤링_실패시_실패_상태와_에러메시지가_DB에_반영된다() {
        // given
        NewsArticle article = createAndSavePendingArticle("https://example.com/3");

        // when
        persistenceService.saveCrawlFailure(article.getId(), "Connection refused");

        // then
        NewsArticle updated = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(updated.getCrawlStatus()).isEqualTo(CrawlStatus.FAILED);
        assertThat(updated.getCrawlRetryCount()).isEqualTo(1);
        assertThat(updated.getCrawlErrorMessage()).isEqualTo("Connection refused");
        assertThat(updated.getCrawlAttemptedAt()).isNotNull();
    }

    @Test
    void 크롤링_실패시_재시도마다_retryCount가_증가한다() {
        // given
        NewsArticle article = createAndSavePendingArticle("https://example.com/4");

        // when
        persistenceService.saveCrawlFailure(article.getId(), "1차 실패");
        persistenceService.saveCrawlFailure(article.getId(), "2차 실패");
        persistenceService.saveCrawlFailure(article.getId(), "3차 실패");

        // then
        NewsArticle updated = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(updated.getCrawlRetryCount()).isEqualTo(3);
        assertThat(updated.getCrawlErrorMessage()).isEqualTo("3차 실패");
    }

    @Test
    void 실패했던_기사가_성공하면_에러메시지는_초기화되고_상태는_SUCCESS가_된다() {
        // given — 먼저 실패
        NewsArticle article = createAndSavePendingArticle("https://example.com/5");
        persistenceService.saveCrawlFailure(article.getId(), "Connection refused");

        // when — 재시도 후 성공
        persistenceService.saveCrawlSuccess(article.getId(), "크롤링 성공 본문", "https://example.com/thumb.jpg");

        // then — 에러 정보 초기화, retryCount는 유지
        NewsArticle updated = newsArticleRepository.findById(article.getId()).orElseThrow();
        assertThat(updated.getCrawlStatus()).isEqualTo(CrawlStatus.SUCCESS);
        assertThat(updated.getContent()).isEqualTo("크롤링 성공 본문");
        assertThat(updated.getCrawlErrorMessage()).isNull();
        assertThat(updated.getCrawlRetryCount()).isEqualTo(1);
    }

    private NewsArticle createAndSavePendingArticle(String url) {
        NewsArticle article = NewsArticle.builder()
                .rawNewsArticleId(null)
                .publisherName("example.com")
                .title("테스트 기사")
                .content("요약")
                .originalUrl(url)
                .dedupKey("url:" + url)
                .build();
        return newsArticleRepository.saveAndFlush(article);
    }
}

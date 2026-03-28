package com.solv.wefin.domain.news.crawl;

import com.solv.wefin.domain.news.crawl.extractor.ArticleContentExtractor;
import com.solv.wefin.domain.news.entity.NewsArticle;
import com.solv.wefin.domain.news.entity.NewsArticle.CrawlStatus;
import com.solv.wefin.domain.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleCrawlService {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; WefinBot/1.0)";
    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;
    private static final int MAX_RETRY = 3;
    private static final int CRAWL_BATCH_SIZE = 500;

    private final NewsArticleRepository newsArticleRepository;
    private final ArticleCrawlPersistenceService persistenceService;
    private final List<ArticleContentExtractor> extractors;
    private final RestTemplate newsRestTemplate;

    /**
     * PENDING/FAILED 상태의 기사를 조회하여 원문 HTML을 크롤링하고 본문/썸네일을 저장한다.
     */
    public void crawlPendingArticles() {
        List<NewsArticle> targets = findCrawlTargets();
        log.info("크롤링 대상 기사 수: {}", targets.size());

        int successCount = 0;
        int failCount = 0;

        for (NewsArticle article : targets) {
            if (crawlSingleArticle(article)) {
                successCount++;
            } else {
                failCount++;
            }
        }

        log.info("크롤링 완료 - 성공: {}, 실패: {}", successCount, failCount);
    }

    private List<NewsArticle> findCrawlTargets() {
        return newsArticleRepository
                .findByCrawlStatusInAndCrawlRetryCountLessThanOrderByCollectedAtDesc(
                        List.of(CrawlStatus.PENDING, CrawlStatus.FAILED),
                        MAX_RETRY,
                        PageRequest.of(0, CRAWL_BATCH_SIZE));
    }

    private boolean crawlSingleArticle(NewsArticle article) {
        try {
            String html = fetchHtml(article.getOriginalUrl());
            if (html == null || html.isBlank()) {
                persistenceService.saveCrawlFailure(article.getId(), "Empty HTML response");
                return false;
            }

            Document doc = Jsoup.parse(html, article.getOriginalUrl());
            return extractAndSave(article, doc);

        } catch (Exception e) {
            handleCrawlException(article, e);
            return false;
        }
    }

    private boolean extractAndSave(NewsArticle article, Document doc) {
        Optional<String> content = extractContent(article.getOriginalUrl(), doc);
        if (content.isEmpty()) {
            persistenceService.saveCrawlFailure(article.getId(), "Content extraction returned empty");
            return false;
        }

        String thumbnailUrl = extractOgImage(doc).orElse(null);
        persistenceService.saveCrawlSuccess(article.getId(), content.get(), thumbnailUrl);
        log.debug("크롤링 성공 - id: {}, contentLength: {}", article.getId(), content.get().length());
        return true;
    }

    private void handleCrawlException(NewsArticle article, Exception e) {
        String errorMessage = truncateErrorMessage(e);
        persistenceService.saveCrawlFailure(article.getId(), errorMessage);
        log.warn("크롤링 실패 - id: {}, url: {}, error: {}",
                article.getId(), article.getOriginalUrl(), e.getMessage());
    }

    private String truncateErrorMessage(Exception e) {
        if (e.getMessage() == null) {
            return "Unknown error";
        }
        return e.getMessage().substring(0, Math.min(e.getMessage().length(), ERROR_MESSAGE_MAX_LENGTH));
    }

    private String fetchHtml(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        ResponseEntity<String> response = newsRestTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return response.getBody();
    }

    private Optional<String> extractOgImage(Document doc) {
        Element ogImage = doc.selectFirst("meta[property=og:image]");
        if (ogImage == null) {
            return Optional.empty();
        }
        String content = ogImage.attr("content");
        return content.isBlank() ? Optional.empty() : Optional.of(content);
    }

    private Optional<String> extractContent(String url, Document doc) {
        for (ArticleContentExtractor extractor : extractors) {
            if (extractor.supports(url)) {
                String content = extractor.extract(doc);
                if (content != null && !content.isBlank()) {
                    return Optional.of(content);
                }
            }
        }
        return Optional.empty();
    }
}
